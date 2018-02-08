package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class PlacementRuleIsValid implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        List<PodSpec> podSpecs = newConfig.getPods().stream()
                .filter(podSpec -> podSpec.getPlacementRule().isPresent())
                .collect(Collectors.toList());

        List<ConfigValidationError> errors = new ArrayList<>();
        for (final PodSpec podSpec : podSpecs) {
            PlacementRule placementRule = podSpec.getPlacementRule().get();

            if (!placementRule.isValid()) {
                String errMsg = String.format(
                        "The PlacementRule for PodSpec '%s' had invalid constraints",
                        podSpec.getType());
                errors.add(ConfigValidationError.valueError("PlacementRule", placementRule.toString(), errMsg));
            }
        }

        return errors;
    }
}
