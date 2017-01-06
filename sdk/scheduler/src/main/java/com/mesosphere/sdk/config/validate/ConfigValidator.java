package com.mesosphere.sdk.config.validate;

import java.util.Collection;

import com.mesosphere.sdk.config.Configuration;

/**
 * The {@code ConfigurationValidation} interface should be implemented by any class which intends to
 * validate a new {@code Configuration}, either on its own, or w.r.t. a prior {@code Configuration}.
 *
 * @param <C> the type of configuration to be validated
 */
public interface ConfigValidator<C extends Configuration> {
    /**
     * Returns {@code List} of {@code ConfigurationValidationError}s for the newly supplied
     * {@code Configuration} object.
     *
     * A validation can validate a newConfig in following ways:
     * 1. Validate newConfig parameters against the oldConfig paramater.
     * Ex: If DiskType was ROOT in oldConfig, then it cannot be changed to, ex: MOUNT, in the newConfig.
     * 2. Validate just newConfig parameter(s). Ex: CPU value > 0
     *
     * @param nullableOldConfig Currently persisted Configuration, or {@code null} if none is
     *                          available (first launch of service)
     * @param newConfig Proposed new Configuration
     * @return List of errors, or an empty list if validation passed
     */
    Collection<ConfigValidationError> validate(C nullableOldConfig, C newConfig);
}
