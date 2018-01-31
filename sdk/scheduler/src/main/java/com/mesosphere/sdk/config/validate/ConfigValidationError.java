package com.mesosphere.sdk.config.validate;

/**
 * Signals that a certain configuration value, or transition between two configurations, did not
 * pass validation.
 */
public class ConfigValidationError {
    private final String configField;
    private final String oldConfigValue; // Nullable
    private final String newConfigValue;
    private final String message;

    /**
     * Returns a new validation error which indicates that a configuration field has an invalid
     * value. This is equivalent to a transition error, except with no prior value.
     */
    public static ConfigValidationError valueError(
            String configField, String configValue, String message) {
        // Set oldValue to null
        return new ConfigValidationError(configField, null, configValue, message);
    }

    /**
     * Returns a new validation error which indicates that a configuration field has an invalid
     * transition from its previous value to the current value.
     */
    public static ConfigValidationError transitionError(
            String configField, String oldConfigValue, String newConfigValue, String message) {
        return new ConfigValidationError(configField, oldConfigValue, newConfigValue, message);
    }

    private ConfigValidationError(
            String configField, String oldConfigValue, String newConfigValue, String message) {
        this.configField = configField;
        this.oldConfigValue = oldConfigValue;
        this.newConfigValue = newConfigValue;
        this.message = message;
    }

    /**
     * Returns the name of the field which had the error.
     */
    public String getConfigurationField() {
        return configField;
    }

    /**
     * Returns the current value which triggered the error.
     */
    public String getConfigurationValue() {
        return newConfigValue;
    }

    /**
     * Returns the previous value which failed to transition to the current value, or {@code null}
     * if this is not a transition error.
     */
    public String getPreviousConfigurationValue() {
        return oldConfigValue;
    }

    /**
     * Returns the provided error message for this error.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns a complete user-facing string representation providing the error and its source.
     */
    @Override
    public String toString() {
        if (oldConfigValue != null) {
            return String.format("Field: '%s'; Transition: '%s' => '%s'; Message: '%s'",
                    configField, oldConfigValue, newConfigValue, message);
        } else {
            return String.format("Field: '%s'; Value: '%s'; Message: '%s'",
                    configField, newConfigValue, message);
        }
    }
}
