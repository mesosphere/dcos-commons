package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import java.util.*;

/**
 * This class validates that referencing Zones in placement constraints can only support the following transitions.
 *
 * 1. null  --> false
 * 2. true  --> true
 * 3. false --> false
 */
public class ZoneValidator {

    public static Collection<ConfigValidationError> validate(
            Optional<ServiceSpec> oldConfig,
            ServiceSpec newConfig,
            String podType,
            String taskName) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Optional<TaskSpec> oldTask = TaskUtils.getTaskSpec(oldConfig.get(), podType, taskName);
        if (!oldTask.isPresent()) {
            // Maybe the pod or task was renamed? Lets avoid enforcing whether those are rename- able and assume it's OK
            return Collections.emptyList();
        }

        Optional<TaskSpec> newTask = TaskUtils.getTaskSpec(newConfig, podType, taskName);
        if (!newTask.isPresent()) {
            throw new IllegalArgumentException(String.format("Unable to find requested pod=%s, task=%s in config: %s",
                    podType, taskName, newConfig));
        }

        String oldEnv = oldTask.get()
                .getCommand().get()
                .getEnvironment()
                .get(EnvConstants.PLACEMENT_REFERENCED_ZONE_ENV);
        String newEnv = newTask.get()
                .getCommand().get()
                .getEnvironment()
                .get(EnvConstants.PLACEMENT_REFERENCED_ZONE_ENV);

        return validateTransition(oldEnv, newEnv, podType, taskName);
    }

    static List<ConfigValidationError> validateTransition(
            String oldEnv, String newEnv, String podType, String taskName) {
        boolean oldZones = Boolean.valueOf(oldEnv);
        boolean newZones = Boolean.valueOf(newEnv);

        ConfigValidationError error = ConfigValidationError.transitionError(
                String.format("%s.%s.env.%s", podType, taskName, EnvConstants.PLACEMENT_REFERENCED_ZONE_ENV),
                oldEnv, newEnv,
                String.format(
                        "Env value %s cannot change from %s to %s",
                        EnvConstants.PLACEMENT_REFERENCED_ZONE_ENV, oldEnv, newEnv));

        if (oldZones != newZones) {
            return Arrays.asList(error);
        }

        return Collections.emptyList();
    }
}
