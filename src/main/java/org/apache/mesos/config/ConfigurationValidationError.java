package org.apache.mesos.config;

/**
 * Signals a certain configurationField is incorrectly configured.
 */
public class ConfigurationValidationError {
    private final String configurationField;
    private final String configurationValue;
    private final String message;

    public ConfigurationValidationError(String configField, String configValue, String message) {
        this.configurationField = configField;
        this.configurationValue = configValue;
        this.message = message;
    }

    public String getConfigurationField() {
        return configurationField;
    }

    public String getConfigurationValue() {
        return configurationValue;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("Validation error. Field: '%s'; Value: '%s'; Message: '%s'",
                configurationField, configurationValue, message);
    }
}
