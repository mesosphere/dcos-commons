package com.mesosphere.sdk.specification.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines annotation for validating Scalar Protos for non-zero value.
 */
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = PositiveScalarProtoValueValidator.class)
@Documented
public @interface PositiveScalarProtoValue {
    String message() default "{com.mesosphere.sdk.specification.validation.PositiveScalarProtoValue.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
