package org.apache.mesos.specification.validation;

import javax.validation.*;
import java.util.Objects;
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
        Validator validator = validatorFactory.getValidator();

        Set<ConstraintViolation<T>> violations = validator.validate(object);

        validatorFactory.close();
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(Objects.toString(violations), violations);
        }
    }
}
