package org.apache.mesos.config;

import java.util.Collection;

/**
 * Reports all the validation errors for a given {@code Configuration}.
 */
public class ConfigValidationException extends Exception {
    private final Collection<ConfigValidationError> validationErrors;

    public ConfigValidationException(Collection<ConfigValidationError> validationErrors) {
        super(String.format("%d validation errors: %s",
                validationErrors.size(), validationErrors));
        this.validationErrors = validationErrors;
    }

    public Collection<ConfigValidationError> getValidationErrors() {
        return validationErrors;
    }
}
