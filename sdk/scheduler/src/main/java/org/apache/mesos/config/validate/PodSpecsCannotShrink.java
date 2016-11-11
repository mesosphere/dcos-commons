package org.apache.mesos.config.validate;

import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.ServiceSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sample configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class PodSpecsCannotShrink implements ConfigurationValidator<ServiceSpec> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        if (nullableOldConfig == null) {
            // No sizes to compare.
            return errors;
        }

        Map<String, PodSpec> newPods;
        try {
            newPods = newConfig.getPods().stream()
                    .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));
        } catch (IllegalStateException e) {
            errors.add(ConfigurationValidationError.valueError("PodSpecs", "null", "Duplicate pod types detected."));
            return errors;
        }

        // Check for PodSpecs in the old config which are missing or smaller in the new config.
        // Adding new PodSpecs or increasing the size of tasksets are allowed.
        for (PodSpec oldPod : nullableOldConfig.getPods()) {
            PodSpec newPod = newPods.get(oldPod.getType());
            if (newPod == null) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("PodSpec[name:%s]", oldPod.getType()),
                        String.valueOf(oldPod.getCount()),
                        "null",
                        String.format("New config is missing PodSpec named '%s' (expected present with >= %d tasks)",
                                oldPod.getType(), oldPod.getCount())));
            } else if (newPod.getCount() < oldPod.getCount()) {
                errors.add(ConfigurationValidationError.transitionError(
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
