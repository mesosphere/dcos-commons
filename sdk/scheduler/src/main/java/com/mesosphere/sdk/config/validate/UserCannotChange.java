package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;

/**
 * Configuration validator which validates that a {@link com.mesosphere.sdk.specification.ServiceSpec}'s user
 * cannot change between deployments.
 */
public class UserCannotChange implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        // user is uniform across pods...
        String oldUser = oldConfig.get().getPods().get(0).getUser().get();
        String newUser = newConfig.getPods().get(0).getUser().get();

        List<ConfigValidationError> errors = new ArrayList<>();
        if (!oldUser.equals(newUser)) {
            errors.add(ConfigValidationError.transitionError(
                    "user",
                    oldUser,
                    newUser,
                    String.format(
                            "Service user must remain the same across deployments. Expected: '%s', given: '%s'",
                            oldUser, newUser
                    )
            ));
        }
        return errors;
    }
}
