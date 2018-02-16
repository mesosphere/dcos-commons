package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This validator enforces that zones and regions are only referenced by placement rules when the cluster can
 * provide this information in {@link org.apache.mesos.Protos.DomainInfo}s.
 */
public class DomainCapabilityValidator implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (Capabilities.getInstance().supportsDomains()) {
            return Collections.emptyList();
        }

        List<PodSpec> podSpecs = newConfig.getPods().stream()
                .filter(podSpec -> podSpec.getPlacementRule().isPresent())
                .collect(Collectors.toList());

        List<ConfigValidationError> errors = new ArrayList<>();
        for (PodSpec podSpec : podSpecs) {
            PlacementRule placementRule = podSpec.getPlacementRule().get();
            if (PlacementUtils.placementRuleReferencesZone(podSpec)) {
                String errMsg = String.format(
                        "The PlacementRule for PodSpec '%s' may not reference Zones prior to DC/OS 1.11.",
                        podSpec.getType());
                errors.add(ConfigValidationError.valueError("PlacementRule", placementRule.toString(), errMsg));
            }

            if (PlacementUtils.placementRuleReferencesRegion(podSpec)) {
                String errMsg = String.format(
                        "The PlacementRule for PodSpec '%s' may not reference Regions prior to DC/OS 1.11.",
                        podSpec.getType());
                errors.add(ConfigValidationError.valueError("PlacementRule", placementRule.toString(), errMsg));
            }
        }

        return errors;
    }
}
