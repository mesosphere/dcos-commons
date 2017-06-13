package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.antlr.v4.runtime.misc.Pair;

import java.util.*;

/**
 * Created by gabriel on 6/12/17.
 */
public class PreReservationCannotChange implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {

        Pair<List<ConfigValidationError>, Map<String, PodSpec>> pair = validateInitialConfigs(nullableOldConfig,
                newConfig);
        List<ConfigValidationError> errors = pair.a;
        Map<String, PodSpec> newPods = pair.b;


        if (nullableOldConfig == null) {
            return errors;
        }

        for (PodSpec oldPod : nullableOldConfig.getPods()) {
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
