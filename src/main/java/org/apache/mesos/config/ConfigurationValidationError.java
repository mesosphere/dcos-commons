package org.apache.mesos.config;

/**
 * Signals a certain configurationField is incorrectly configured.
 */
public class ConfigurationValidationError {
    private final String configurationField;
    private final String message;

    public ConfigurationValidationError(String configField, String message) {
        this.configurationField = configField;
        this.message = message;
    }

    public String getConfigurationField() {
        return configurationField;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("Validation error. Field: '%s'; Message: '%s'", configurationField, message);
    }
}
