package org.apache.mesos.config;

import java.util.List;

/**
 * The {@code ConfigValidation} interface should be implemented by any class
 * which intend to validate a new {@code Configuration} w.r.t. an old {@code Configuration}.
 */
public interface ConfigValidation {
    /**
     * Returns {@code List} of {@code ConfigValidationError} for the newly supplied
     * {@code Configuration} object.
     *
     * @param oldConfig Currently persisted Configuration.
     * @param newConfig Proposed new Configuration
     * @return List of errors
     */
    List<ConfigValidationError> validate(Configuration oldConfig, Configuration newConfig);
}
