package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class validates that referencing Regions in placement constraints can only support the following transitions.
 *
 * 1. null  --> false
 * 2. true  --> true
 * 3. false --> false
 */
public class RegionValidator {
    public static Collection<ConfigValidationError> validate(
            Optional<ServiceSpec> oldConfig, ServiceSpec newConfig, String... podTypes) {
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
            return Collections.emptyList();
        }

        Optional<PodSpec> newPod = getPodSpec(newConfig, podType);
        if (!newPod.isPresent()) {
            throw new IllegalArgumentException(String.format("Unable to find requested pod=%s, in config: %s",
                    podType, newConfig));
        }

        boolean oldReferencesRegions = PlacementUtils.placementRuleReferencesRegion(oldPod.get());
        boolean newReferencesRegions = PlacementUtils.placementRuleReferencesRegion(newPod.get());

        if (oldReferencesRegions != newReferencesRegions) {
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
