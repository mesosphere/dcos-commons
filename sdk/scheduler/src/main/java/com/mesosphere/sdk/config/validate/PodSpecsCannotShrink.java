package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class PodSpecsCannotShrink implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Map<String, PodSpec> newPods;
        try {
            newPods = newConfig.getPods().stream()
                    .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));
        } catch (IllegalStateException e) {
            return Arrays.asList(
                    ConfigValidationError.valueError("PodSpecs", "null", "Duplicate pod types detected."));
        }

        // Check for PodSpecs in the old config which are missing or smaller in the new config.
        // Adding new PodSpecs or increasing the size of tasksets are allowed.
        Collection<ConfigValidationError> errors = new ArrayList<>();
        for (PodSpec oldPod : oldConfig.get().getPods()) {
            PodSpec newPod = newPods.get(oldPod.getType());
            if (newPod == null) {
                // Prior pod of this name is now missing. Allow the removal only if the old pod's allow-decommission bit
                // was set. This could be used as an (admittedly convoluted) path for migrating away from an old service
                // layout, with an interim release marking a pod with allow-decommission before it's removed entirely.
                if (!oldPod.getAllowDecommission()) {
                    errors.add(ConfigValidationError.transitionError(
                            String.format("PodSpec[name:%s]", oldPod.getType()),
                            String.valueOf(oldPod.getCount()),
                            "null",
                            String.format(
                                    "New config is missing PodSpec named '%s' (expected present with >= %d tasks)",
                                    oldPod.getType(), oldPod.getCount())));
                }
                continue;
            }

            if (newPod.getCount() < oldPod.getCount() && !newPod.getAllowDecommission()) {
                // New pod is present and is smaller than old pod.
                // Only allow the new pod to be shrunk if its allow-decommission bit is set. We intentionally check
                // allow-decommission against the new pod instead of the old pod with the following reasoning:
                // - Older version disallows, newer version allows: Allow count to decrease now that it's supported
                // - Older version allows, newer version disallows: Disallow count from decreasing now that it's no
                //   longer supported
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", newPod.getType()),
                        String.valueOf(oldPod.getCount()),
                        String.valueOf(newPod.getCount()),
                        String.format("New config's PodSpec named '%s' has %d tasks, expected >=%d tasks",
                                newPod.getType(), newPod.getCount(), oldPod.getCount())));
            }
        }

        return errors;
    }
}
