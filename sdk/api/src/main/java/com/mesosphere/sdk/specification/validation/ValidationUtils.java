package com.mesosphere.sdk.specification.validation;

import javax.validation.*;
import java.util.Set;

/**
 * Various utilities for spec validation.
 */
public class ValidationUtils {
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ValidationUtils() {
        // do not instantiate
    }

    public static <T> void validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(
                    String.format("Validation failed for object:%n%s%nViolations:%n%s", object, violations),
                    violations);
        }
    }
}
