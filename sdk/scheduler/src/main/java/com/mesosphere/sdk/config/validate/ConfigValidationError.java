package com.mesosphere.sdk.config.validate;

import javax.annotation.Nullable;

/**
 * Signals that a certain configuration value, or transition between two configurations, did not
 * pass validation.
 */
public final class ConfigValidationError {
  private final String configField;

  @Nullable
  private final String oldConfigValue;

  private final String newConfigValue;

  private final String message;

  private final boolean isFatal;

  private ConfigValidationError(
      String configField,
      String oldConfigValue,
      String newConfigValue,
      String message,
      boolean isFatal)
  {
    this.configField = configField;
    this.oldConfigValue = oldConfigValue;
    this.newConfigValue = newConfigValue;
    this.message = message;
    this.isFatal = isFatal;
  }

  /**
   * Returns a new validation error which indicates that a configuration field has an invalid
   * value. This is equivalent to a transition error, except with no prior value.
   */
  public static ConfigValidationError valueError(
      String configField, String configValue, String message)
  {
    return valueError(configField, configValue, message, false);
  }

  /**
   * Returns a new validation error which indicates that a configuration field has an invalid
   * value. This is equivalent to a transition error, except with no prior value.
   */
  public static ConfigValidationError valueError(
      String configField, String configValue, String message, boolean isFatal)
  {
    // Set oldValue to null
    return new ConfigValidationError(configField, null, configValue, message, isFatal);
  }

  /**
   * Returns a new validation error which indicates that a configuration field has an invalid
   * transition from its previous value to the current value.
   */
  public static ConfigValidationError transitionError(
      String configField, String oldConfigValue, String newConfigValue, String message)
  {
    return transitionError(configField, oldConfigValue, newConfigValue, message, false);
  }

  /**
   * Returns a new validation error which indicates that a configuration field has an invalid
   * transition from its previous value to the current value.
   */
  public static ConfigValidationError transitionError(
      String configField, String oldConfigValue, String newConfigValue, String message,
      boolean isFatal)
  {
    return new ConfigValidationError(configField, oldConfigValue, newConfigValue, message, isFatal);
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
   * Returns if this error message is fatal.
   */
  public boolean isFatal() {
    return isFatal;
  }

  /**
   * Returns a complete user-facing string representation providing the error and its source.
   */
  @Override
  public String toString() {
    if (oldConfigValue != null) {
      return String.format("Field: '%s'; Transition: '%s' => '%s'; Message: '%s'; Fatal: %s",
          configField, oldConfigValue, newConfigValue, message, isFatal);
    } else {
      return String.format("Field: '%s'; Value: '%s'; Message: '%s'; Fatal: %s",
          configField, newConfigValue, message, isFatal);
    }
  }
}
