package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import javafx.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sample configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class PodSpecsCannotShrink implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        Pair<List<ConfigValidationError>, Map<String, PodSpec>> pair = validateInitialConfigs(nullableOldConfig,
                newConfig);
        List<ConfigValidationError> errors = pair.getKey();
        Map<String, PodSpec> newPods = pair.getValue();

        if (newPods.isEmpty()) {
            return errors;
        }

        // Check for PodSpecs in the old config which are missing or smaller in the new config.
        // Adding new PodSpecs or increasing the size of tasksets are allowed.
        for (PodSpec oldPod : nullableOldConfig.getPods()) {
            PodSpec newPod = newPods.get(oldPod.getType());
            if (newPod == null) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", oldPod.getType()),
                        String.valueOf(oldPod.getCount()),
                        "null",
                        String.format("New config is missing PodSpec named '%s' (expected present with >= %d tasks)",
                                oldPod.getType(), oldPod.getCount())));
            } else if (newPod.getCount() < oldPod.getCount()) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[setname:%s]", newPod.getType()),
                        String.valueOf(oldPod.getCount()),
                        String.valueOf(newPod.getCount()),
                        String.format("New config's PodSpec named '%s' has %d tasks, expected >=%d tasks",
                                newPod.getType(), newPod.getCount(), oldPod.getCount())));
            }
        }

        return errors;
    }
}
