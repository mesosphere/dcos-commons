package org.apache.mesos.config;

import java.util.Collection;

/**
 * Reports all the validation errors for a given {@code Configuration}.
 */
public class ConfigurationValidationException extends Exception {
    private final Collection<ConfigurationValidationError> validationErrors;

    public ConfigurationValidationException(Collection<ConfigurationValidationError> validationErrors) {
        super(String.format("%d validation errors: %s",
                validationErrors.size(), validationErrors));
        this.validationErrors = validationErrors;
    }

    public Collection<ConfigurationValidationError> getValidationErrors() {
        return validationErrors;
    }
}
