package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that the pre-reserved-role of a Pod cannot change.
 */
public class PreReservationCannotChange implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Map<String, PodSpec> newPods = newConfig.getPods().stream()
                .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));

        List<ConfigValidationError> errors = new ArrayList<>();
        for (PodSpec oldPod : oldConfig.get().getPods()) {
            PodSpec newPod = newPods.get(oldPod.getType());
            if (!newPod.getPreReservedRole().equals(oldPod.getPreReservedRole())) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[pre-reserved-role:%s]", oldPod.getPreReservedRole()),
                        oldPod.getPreReservedRole(),
                        newPod.getPreReservedRole(),
                        String.format(
                                "New config has changed the pre-reserved-role of PodSpec named '%s'" +
                                        " (expected it to stay '%s')",
                                oldPod.getType(), oldPod.getPreReservedRole())));
            }
        }

        return errors;
    }
}
