package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;

/**
 * Configuration validator which validates that a {@link com.mesosphere.sdk.specification.ServiceSpec}'s region
 * cannot change between deployments.
 *
 * This is to prevent a pod deployed in one region from being deployed in another region if it is replaced, since we
 * do not support multi-region deployments. Since region awareness is implemented with placement constraints,
 * a pod replace would otherwise break this assumption.
 */
public class RegionCannotChange implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        List<ConfigValidationError> errors = new ArrayList<>();
        if (oldConfig.get().getRegion() != null && !oldConfig.get().getRegion().equals(newConfig.getRegion())) {
            errors.add(ConfigValidationError.transitionError(
                    "region",
                    oldConfig.get().getUser(),
                    newConfig.getUser(),
                    "Region for old service must remain the same across deployments."
            ));
        }

        return errors;
    }
}

