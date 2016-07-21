package org.apache.mesos.config;

import java.util.List;

/**
 * The {@code ConfigurationValidation} interface should be implemented by any class
 * which intend to validate a new {@code Configuration} w.r.t. an old {@code Configuration}.
 */
public interface ConfigurationValidation {
    /**
     * Returns {@code List} of {@code ConfigurationValidationError} for the newly supplied
     * {@code Configuration} object.
     *
     * @param oldConfig Currently persisted Configuration.
     * @param newConfig Proposed new Configuration
     * @return List of errors, or an empty list if validation passed
     */
    List<ConfigurationValidationError> validate(Configuration oldConfig, Configuration newConfig);
}
