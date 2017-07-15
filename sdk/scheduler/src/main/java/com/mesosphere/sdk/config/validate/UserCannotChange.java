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
            "User for old pod type %s must remain the same across deployments. Expected: '%s', given: '%s'";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        List<ConfigValidationError> errors = new ArrayList<>();

        // The corresponding old and new pods must have the same user.
        Iterator<PodSpec> oldPods = oldConfig.get().getPods().iterator();
        Iterator<PodSpec> newPods = newConfig.getPods().iterator();
        PodSpec oldPod = null, newPod;
        String oldUser = "", newUser;

        while (oldPods.hasNext() && newPods.hasNext()) {
            oldPod = oldPods.next();
            newPod = newPods.next();

            // in case old user is set but not new user or reverse
            if (!userStatesMatch(oldPod, newPod, errors)) {
               return errors;
            }

            // if old pod user is present then so is new pod user according to the check above
            if (oldPod.getUser().isPresent()) {
                checkForUserEquality(oldPod, newPod, errors);
            }
        }
        // If the new config has more pods than the previous config than the new pods can set the user
        // however the developer intends to. We're only concerned about not updating the user for existing pods
        // in this validation.

        return errors;
    }

    private boolean userStatesMatch(PodSpec oldPod, PodSpec newPod, List<ConfigValidationError> errors) {
        if (!oldPod.getUser().isPresent() && newPod.getUser().isPresent()) {
            errors.add(
                    ConfigValidationError.transitionError(
                    "user",
                    null,
                    newPod.getUser().get(),
                    String.format(USER_CHANGED_ERROR_MESSAGE,
                            oldPod.getType(),
                            null,
                            newPod.getUser().get())
            ));
            return false;
        } else if (oldPod.getUser().isPresent() && !newPod.getUser().isPresent()) {
            errors.add(
                    ConfigValidationError.transitionError(
                            "user",
                            oldPod.getUser().get(),
                            null,
                            String.format(USER_CHANGED_ERROR_MESSAGE,
                                    oldPod.getType(),
                                    oldPod.getUser().get(),
                                    null)
                    ));
            return false;
        }

        return true;
    }

    private void checkForUserEquality(PodSpec oldPod, PodSpec newPod, List<ConfigValidationError> errors) {
        String oldUser = oldPod.getUser().get();
        String newUser = newPod.getUser().get();

        if (!oldUser.equals(newUser)) {
            errors.add(ConfigValidationError.transitionError(
                    "user",
                    oldUser,
                    newUser,
                    String.format(USER_CHANGED_ERROR_MESSAGE, oldPod.getType(), oldUser, newUser)
            ));
        }
    }
}
