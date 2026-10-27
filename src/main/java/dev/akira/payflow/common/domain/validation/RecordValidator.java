package dev.akira.payflow.common.domain.validation;

import java.lang.reflect.RecordComponent;
import java.util.Objects;

/**
 * Validates custom constraints declared on Java record components.
 *
 * <p>Currently, this validator supports the {@link NotNull} annotation.
 * Each annotated component is read through its accessor, and its value
 * is checked for {@code null}.
 *
 * <p>This validator must be invoked explicitly. It is not automatically
 * integrated with Java record construction or Spring validation.
 *
 * <p>Null values in annotated components result in an
 * {@link IllegalArgumentException}. Reflection failures result in an
 * {@link IllegalStateException}
 *
 * @author akira
 * @since 0.0.1-SNAPSHOT
 *
 * @see NotNull
 * @see RecordNullabilityTransformer
 */
public final class RecordValidator {

    /**
     * Prevents instantiation of this utility class.
     */
    private RecordValidator() {
    }

    /**
     *
     * @param record the record instance to validate
     * @throws NullPointerException if {@code record} is {@code null}
     * @throws IllegalArgumentException if the object is not a record,
     *         or an annotated component violates its constraint
     * @throws IllegalStateException if a component accessor cannot be invoked
     */
    public static void validate(Object record) {
        Objects.requireNonNull(record, "Record must not be null");

        Class<?> type = record.getClass();

        if (!type.isRecord()) {
            throw new IllegalArgumentException(
                "Expected a record: " + type.getName()
            );
        }

        for (RecordComponent component : type.getRecordComponents()) {
            NotNull annotation = component.getAnnotation(NotNull.class);

            if (annotation == null) {
                continue;
            }

            try {
                Object value = component.getAccessor().invoke(record);

                if (value == null) {
                    throw new IllegalArgumentException(
                        type.getSimpleName()
                            + "."
                            + component.getName()
                            + " "
                            + annotation.message()
                    );
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                    "Failed to validate "
                        + type.getName()
                        + "."
                        + component.getName(),
                    exception
                );
            }
        }
    }
}
