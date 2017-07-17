package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;

/**
 * Configuration validator which validates that a {@link com.mesosphere.sdk.specification.ServiceSpec}'s user
 * cannot change between deployments.
 *
 * This is to prevent change in user-level permissions while accessing files across updates. If a
 * less-privileged user replaces a more-privilege user (eg. "root" -> "nobody"), this is a breaking change
 * as "nobody" will not have access to "root"-owned files in persistent volumes.
 */
public class UserCannotChange implements ConfigValidator<ServiceSpec> {
    private static final String USER_CHANGED_ERROR_MESSAGE =
            "User for old pod type %s must remain the same across deployments.";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        List<ConfigValidationError> errors = new ArrayList<>();
        // We can't rely on the order of pods to test new pods against olds ones.
        Map<String, PodSpec> oldPods = new HashMap<>();
        for (PodSpec oldPod : oldConfig.get().getPods()) {
            oldPods.put(oldPod.getType(), oldPod);
        }

        for (PodSpec newPod : newConfig.getPods()) {
            // If a new pod type is introduced, we don't validate how its user is set as we're only concerned
            // about not updating the user for existing pods in this validation.
            if (oldPods.containsKey(newPod.getType())) {
                checkForUserEquality(oldPods.get(newPod.getType()), newPod, errors);
            }
        }

        return errors;
    }

    private void checkForUserEquality(PodSpec oldPod, PodSpec newPod, List<ConfigValidationError> errors) {
        if (!oldPod.getUser().equals(newPod.getUser())) {
            errors.add(ConfigValidationError.transitionError(
                    "user",
                    oldPod.getUser().isPresent() ? oldPod.getUser().get() : null,
                    newPod.getUser().isPresent() ? newPod.getUser().get() : null,
                    String.format(USER_CHANGED_ERROR_MESSAGE, oldPod.getType())
            ));
        }
    }
}
