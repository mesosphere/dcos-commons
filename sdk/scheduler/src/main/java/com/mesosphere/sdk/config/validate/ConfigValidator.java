package com.mesosphere.sdk.config.validate;

import java.util.*;
import java.util.stream.Collectors;

import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.antlr.v4.runtime.misc.Pair;

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

    default Pair<List<ConfigValidationError>, Map<String, PodSpec>> validateInitialConfigs(
            ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        List<ConfigValidationError> errors = new ArrayList<>();
        if (nullableOldConfig == null) {
            // No sizes to compare.

            return new Pair<>(errors, Collections.emptyMap());
        }

        Map<String, PodSpec> newPods;
        try {
            newPods = newConfig.getPods().stream()
                    .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));
        } catch (IllegalStateException e) {
            errors.add(ConfigValidationError.valueError("PodSpecs", "null", "Duplicate pod types detected."));
            return new Pair<>(errors, Collections.emptyMap());
        }

        return new Pair<>(errors, newPods);
    }
}
