package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that the pre-reserved-role of a Pod cannot change.
 */
public class PreReservationCannotChange implements ConfigValidator<ServiceSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreReservationCannotChange.class);

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Map<String, PodSpec> newPods = newConfig.getPods().stream()
                .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));

        List<ConfigValidationError> errors = new ArrayList<>();
        for (PodSpec oldPod : oldConfig.get().getPods()) {
            final PodSpec newPod = newPods.get(oldPod.getType());

            if (newPod == null) {
                continue;
            }

            if (!StringUtils.equals(newPod.getPreReservedRole(), oldPod.getPreReservedRole())) {
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
