package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.commons.lang3.StringUtils;

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

        final Map<String, PodSpec> newPods = newConfig.getPods().stream()
                .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));

        final List<ConfigValidationError> errors = new ArrayList<>();
        for (final PodSpec oldPod : oldConfig.get().getPods()) {
            final PodSpec newPod = newPods.get(oldPod.getType());

            if (newPod == null) {
                continue;
            }

            final String oldPodPreReservedRole = oldPod.getPreReservedRole();
            final String newPodPreReservedRole = newPod.getPreReservedRole();

            if (!StringUtils.equals(newPodPreReservedRole, oldPodPreReservedRole)) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[pre-reserved-role:%s]", oldPodPreReservedRole),
                        oldPodPreReservedRole,
                        newPodPreReservedRole,
                        String.format(
                                "New config has changed the pre-reserved-role of PodSpec named '%s'" +
                                        " (expected it to stay '%s')",
                                oldPod.getType(), oldPodPreReservedRole)));
            }
        }

        return errors;
    }
}
