package dev.akira.payflow.common.domain.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a record component must not contain {@code null}.
 *
 * <p>This annotation is retained at runtime so that
 * {@link RecordValidator} can inspect it through reflection and
 * {@link RecordNullabilityTransformer} can use it to inject
 * null checks into compiled record constructors.
 *
 * <p>Declaring this annotation alone does not enforce the constraint.
 * Enforcement depends on the validation mechanism being used.
 *
 * @author akira
 * @since 0.0.1-SNAPSHOT
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface NotNull {
    /**
     * Returns the message describing the nullability constraint.
     *
     * @return the constraint message
     */
    String message() default "must not be null";
}
