package org.apache.mesos.util;

import javax.validation.*;
import java.util.Objects;
import java.util.Set;

/**
 * Various utilities for Validation.
 */
public class ValidationUtils {
    public static <T> void validate(T object) {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();

        Set<ConstraintViolation<T>> violations = validator.validate(object);

        validatorFactory.close();
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(Objects.toString(violations), violations);
        }
    }
}
