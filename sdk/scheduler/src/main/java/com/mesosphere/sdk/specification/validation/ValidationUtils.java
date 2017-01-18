package com.mesosphere.sdk.specification.validation;

import javax.validation.*;
import java.util.Set;

/**
 * Various utilities for spec validation.
 */
public class ValidationUtils {

    private ValidationUtils() {
        // do not instantiate
    }

    public static <T> void validate(T object) {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Set<ConstraintViolation<T>> violations = validatorFactory.getValidator().validate(object);
        validatorFactory.close();

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(
                    String.format("Validation failed for object:%n%s%nViolations:%n%s", object, violations),
                    violations);
        }
    }
}
