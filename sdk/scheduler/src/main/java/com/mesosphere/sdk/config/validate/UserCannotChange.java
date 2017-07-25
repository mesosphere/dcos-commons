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
        if (oldConfig.get().getUser() != null && !oldConfig.get().getUser().equals(newConfig.getUser())) {
            errors.add(ConfigValidationError.transitionError(
                    "user",
                    oldConfig.get().getUser(),
                    newConfig.getUser(),
                    "User for old service must remain the same across deployments."
            ));
        }

        // We can't rely on the order of pods to test new pods against olds ones.
        Map<String, PodSpec> oldPods = new HashMap<>();
        for (PodSpec oldPod : oldConfig.get().getPods()) {
            oldPods.put(oldPod.getType(), oldPod);
        }
        errors.addAll(checkForUserEqualityAmongPods(oldPods, newConfig.getPods()));

        return errors;
    }

    private List<ConfigValidationError> checkForUserEqualityAmongPods(
            Map<String, PodSpec> oldPods, List<PodSpec> newPods) {
        List<ConfigValidationError> errors = new ArrayList<>();
        PodSpec oldPod;
        for (PodSpec newPod : newPods) {
            // If a new pod type is introduced, we don't validate how its user is set as we're only concerned
            // about not updating the user for existing pods in this validation.
            if (oldPods.containsKey(newPod.getType())) {
                oldPod = oldPods.get(newPod.getType());
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
        return errors;
    }
}
