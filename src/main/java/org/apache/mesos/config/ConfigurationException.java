package org.apache.mesos.config;

/**
 * Indicates an exception or poor request for configuration.
 */
public class ConfigurationException extends RuntimeException {

  public ConfigurationException(Throwable cause) {
    super(cause);
  }

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
