package com.mesosphere.sdk.specification.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines UniquePodType annotation.
 */
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = UniquePodTypeValidator.class)
@Documented
public @interface UniquePodType {
    String message() default "{com.mesosphere.sdk.specification.validation.UniquePodType.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
