package com.mesosphere.sdk.config.validate;

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
            "User for service must remain the same across deployments.";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        List<ConfigValidationError> errors = new ArrayList<>();

        String oldUser = oldConfig.get().getUser();
        String newUser = newConfig.getUser();
        if (!oldUser.equals(newUser)) {
            errors.add(ConfigValidationError.transitionError(
                    "user",
                    oldUser,
                    newUser,
                    USER_CHANGED_ERROR_MESSAGE
            ));
        }

        return errors;
    }
}
