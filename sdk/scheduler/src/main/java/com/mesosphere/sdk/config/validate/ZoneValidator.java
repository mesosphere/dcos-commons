package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class validates that referencing Zones in placement constraints can only support the following transitions.
 *
 * 1. null  --> false
 * 2. true  --> true
 * 3. false --> false
 */
public class ZoneValidator {

    private ZoneValidator() {
        // do not instantiate
    }

    public static Collection<ConfigValidationError> validate(
            Optional<ServiceSpec> oldConfig,
            ServiceSpec newConfig,
            String... podTypes) {
        return Arrays.asList(podTypes).stream()
                .flatMap(p -> validate(oldConfig, newConfig, p).stream())
                .collect(Collectors.toList());
    }

    private static Collection<ConfigValidationError> validate(
            Optional<ServiceSpec> oldConfig,
            ServiceSpec newConfig,
            String podType) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Optional<PodSpec> oldPod = getPodSpec(oldConfig.get(), podType);
        if (!oldPod.isPresent()) {
            // Maybe the pod or task was renamed? Lets avoid enforcing whether those are rename- able and assume it's OK
            return Collections.emptyList();
        }

        Optional<PodSpec> newPod = getPodSpec(newConfig, podType);
        if (!newPod.isPresent()) {
            throw new IllegalArgumentException(String.format("Unable to find requested pod=%s, in config: %s",
                    podType, newConfig));
        }

        boolean oldReferencesZones = PlacementUtils.placementRuleReferencesZone(oldPod.get());
        boolean newReferencesZones = PlacementUtils.placementRuleReferencesZone(newPod.get());

        if (oldReferencesZones != newReferencesZones) {
            Optional<PlacementRule> oldRule = oldPod.get().getPlacementRule();
            Optional<PlacementRule> newRule = newPod.get().getPlacementRule();
            ConfigValidationError error = ConfigValidationError.transitionError(
                    String.format("%s.PlacementRule", podType),
                    oldRule.toString(), newRule.toString(),
                    String.format("PlacementRule cannot change from %s to %s", oldRule, newRule));
            return Arrays.asList(error);
        }

        return Collections.emptyList();
    }

    private static Optional<PodSpec> getPodSpec(ServiceSpec serviceSpecification, String podType) {
        return serviceSpecification.getPods().stream()
                .filter(pod -> pod.getType().equals(podType))
                .findFirst();
    }
}
