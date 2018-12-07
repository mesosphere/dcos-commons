package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This validator enforces that zones and regions are only referenced by placement rules when the
 * cluster can provide this information in {@link org.apache.mesos.Protos.DomainInfo}s.
 */
public class DomainCapabilityValidator implements ConfigValidator<ServiceSpec> {
  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
    if (Capabilities.getInstance().supportsDomains()) {
      return Collections.emptyList();
    }

    List<PodSpec> podSpecs = newConfig.getPods().stream()
        .filter(podSpec -> podSpec.getPlacementRule().isPresent())
        .collect(Collectors.toList());

    List<ConfigValidationError> errors = new ArrayList<>();
    for (PodSpec podSpec : podSpecs) {
      PlacementRule placementRule = podSpec.getPlacementRule().get();
      String placementErrorTemplate = "The PlacementRule for PodSpec '%s' may not reference" +
          " %s prior to DC/OS 1.11.";
      if (PlacementUtils.placementRuleReferencesZone(podSpec)) {
        errors.add(ConfigValidationError.valueError(
            // SUPPRESS CHECKSTYLE MultipleStringLiterals
            "PlacementRule",
            placementRule.toString(),
            String.format(placementErrorTemplate, "Zones", podSpec.getType())
        ));
      }

      if (PlacementUtils.placementRuleReferencesRegion(podSpec)) {
        errors.add(ConfigValidationError.valueError(
            "PlacementRule",
            placementRule.toString(),
            String.format(placementErrorTemplate, "Regions", podSpec.getType())
        ));
      }
    }

    return errors;
  }
}
