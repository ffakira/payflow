package dev.akira.payflow.common.domain.validation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Injects runtime null checks into compiled Java record constructors.
 *
 * <p>The transformer scans a compiled classes directory for record classes
 * whose components are annotated with {@link NotNull}. For each matching
 * record, it locates the canonical constructor and inserts a null check
 * for every annotated reference or array component.
 *
 * <p>A null component causes the transformed constructor to throw a
 * {@link NullPointerException} containing the configured annotation
 * message. Components without {@code @NotNull} are left unchanged.
 *
 * <p>This class is intended to run as part of the build process after
 * record classes have been compiled and before they are packaged or used
 * by tests.
 *
 * <p>This transformer does not replace {@link RecordValidator}. The
 * transformer modifies compiled constructors, whereas
 * {@code RecordValidator} checks existing record instances through
 * reflection.
 *
 * <p>The current implementation assumes that the canonical constructor
 * contains the standard {@code java.lang.Record} superclass constructor
 * invocation. Repeated transformation is not explicitly guarded against.
 *
 * @author akira
 * @since 0.0.1-SNAPSHOT
 *
 * @see NotNull
 * @see RecordValidator
 */
public final class RecordNullabilityTransformer {

    private static final String NOT_NULL_DESCRIPTOR =
        Type.getDescriptor(NotNull.class);

    /**
     * Prevents instantiation of this utility class.
     */
    private RecordNullabilityTransformer() {
    }

    /**
     * Transforms compiled record classes in the specified directory.
     *
     * @param args a single argument containing the compiled classes directory
     * @throws IOException if a class file cannot be read or written
     * @throws IllegalArgumentException if the argument count is incorrect
     *         or the specified directory does not exist
     * @throws IllegalStateException if a matching record's canonical
     *         constructor or expected superclass invocation cannot be found
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: RecordNullabilityTransformer <classes-directory>"
            );
        }

        Path classesDirectory = Path.of(args[0]);

        if (!Files.isDirectory(classesDirectory)) {
            throw new IllegalArgumentException(
                "Classes directory does not exist: " + classesDirectory
            );
        }

        try (var paths = Files.walk(classesDirectory)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".class"))
                    .toList()) {
                transform(path);
            }
        }
    }

    /**
     * Transforms a class file when it contains constrained record components.
     *
     * <p>The method locates the canonical constructor, builds null checks
     * for annotated components, inserts the checks immediately after the
     * record superclass constructor invocation, and writes the modified
     * bytecode back to the original file.
     *
     * @param path the compiled class file to inspect
     * @throws IOException if the class file cannot be read or written
     * @throws IllegalStateException if the canonical constructor or expected
     *         superclass constructor invocation cannot be found
     */
    private static void transform(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);

        ClassReader reader = new ClassReader(original);
        ClassNode type = new ClassNode(Opcodes.ASM9);

        reader.accept(type, 0);

        if (type.recordComponents == null
                || type.recordComponents.isEmpty()) {
            return;
        }

        List<RecordComponentNode> components =
            type.recordComponents;

        boolean hasConstraints = components.stream()
            .anyMatch(RecordNullabilityTransformer::isNotNull);

        if (!hasConstraints) {
            return;
        }

        String constructorDescriptor = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            components.stream()
                .map(component -> Type.getType(component.descriptor))
                .toArray(Type[]::new)
        );

        MethodNode constructor = type.methods.stream()
            .filter(method -> method.name.equals("<init>"))
            .filter(method -> method.desc.equals(constructorDescriptor))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Canonical constructor not found for " + type.name
            ));

        AbstractInsnNode superCall = findRecordSuperCall(constructor);

        if (superCall == null) {
            throw new IllegalStateException(
                "Record superclass constructor call not found for "
                    + type.name
            );
        }

        InsnList checks = new InsnList();
        int localIndex = 1;

        for (RecordComponentNode component : components) {
            Type componentType = Type.getType(component.descriptor);

            if (isNotNull(component)) {
                if (componentType.getSort() != Type.OBJECT
                        && componentType.getSort() != Type.ARRAY) {
                    throw new IllegalStateException(
                        "@NotNull is unnecessary on primitive component "
                            + type.name + "." + component.name
                    );
                }

                String message = getMessage(component);

                LabelNode nonNull = new LabelNode();

                checks.add(new VarInsnNode(Opcodes.ALOAD, localIndex));
                checks.add(new JumpInsnNode(
                    Opcodes.IFNONNULL,
                    nonNull
                ));
                checks.add(new TypeInsnNode(
                    Opcodes.NEW,
                    "java/lang/NullPointerException"
                ));
                checks.add(new InsnNode(Opcodes.DUP));
                checks.add(new LdcInsnNode(message));
                checks.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/NullPointerException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                ));
                checks.add(new InsnNode(Opcodes.ATHROW));
                checks.add(nonNull);
            }

            localIndex += componentType.getSize();
        }

        if (checks.size() == 0) {
            return;
        }

        constructor.instructions.insert(superCall, checks);

        ClassWriter writer = new ClassWriter(
            reader,
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
        );

        type.accept(writer);

        Files.write(path, writer.toByteArray());

        System.out.println(
            "Transformed record: " + type.name.replace('/', '.')
        );
    }

    /**
     * Finds the invocation of the {@code java.lang.Record} constructor.
     *
     * @param constructor the canonical record constructor
     * @return the matching instruction, or {@code null} if none is found
     */
    private static AbstractInsnNode findRecordSuperCall(
        MethodNode constructor
    ) {
        for (AbstractInsnNode instruction :
                constructor.instructions) {
            if (instruction instanceof MethodInsnNode method
                    && instruction.getOpcode() == Opcodes.INVOKESPECIAL
                    && method.owner.equals("java/lang/Record")
                    && method.name.equals("<init>")
                    && method.desc.equals("()V")) {
                return instruction;
            }
        }

        return null;
    }

    /**
     * Determines whether a record component declares {@link NotNull}.
     *
     * <p>Both visible and invisible annotation collections are inspected
     * to support either annotation retention in compiled bytecode.
     *
     * @param component the record component to inspect
     * @return {@code true} if the component has {@code @NotNull}
     */
    private static boolean isNotNull(
        RecordComponentNode component
    ) {
        return hasAnnotation(component.visibleAnnotations)
            || hasAnnotation(component.invisibleAnnotations);
    }

    /**
     * Checks whether an annotation collection contains {@link NotNull}.
     *
     * @param annotations the annotations to inspect; may be {@code null}
     * @return {@code true} if {@code @NotNull} is present
     */
    private static boolean hasAnnotation(
        List<AnnotationNode> annotations
    ) {
        return annotations != null && annotations.stream()
            .anyMatch(annotation ->
                annotation.desc.equals(NOT_NULL_DESCRIPTOR)
            );
    }

    /**
     * Resolves the configured {@link NotNull#message()} for a component.
     *
     * <p>If no explicit message value is stored in the class file, the
     * component name is used to construct a fallback message.
     *
     * @param component the annotated record component
     * @return the configured or fallback nullability message
     */
    private static String getMessage(
        RecordComponentNode component
    ) {
        List<AnnotationNode> annotations = new ArrayList<>();

        if (component.visibleAnnotations != null) {
            annotations.addAll(component.visibleAnnotations);
        }

        if (component.invisibleAnnotations != null) {
            annotations.addAll(component.invisibleAnnotations);
        }

        return annotations.stream()
            .filter(annotation ->
                annotation.desc.equals(NOT_NULL_DESCRIPTOR)
            )
            .flatMap(annotation -> {
                if (annotation.values == null) {
                    return java.util.stream.Stream.empty();
                }

                List<Object> values = annotation.values;

                for (int i = 0; i < values.size(); i += 2) {
                    if (Objects.equals(values.get(i), "message")) {
                        return java.util.stream.Stream.of(
                            String.valueOf(values.get(i + 1))
                        );
                    }
                }

                return java.util.stream.Stream.empty();
            })
            .findFirst()
            .orElse(component.name + " must not be null");
    }
}
