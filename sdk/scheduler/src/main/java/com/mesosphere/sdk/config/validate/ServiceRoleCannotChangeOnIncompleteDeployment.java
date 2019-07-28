package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Configuration validator which halts the schdeduler due to a role change on an incoming
 * {@link com.mesosphere.sdk.specification.ServiceSpec} where the previous deployment wasn't complete.
 * <p>
 * This is to prevent the scheduler from continuing from previous incomplete deployments on role changes.
 */
public class ServiceRoleCannotChangeOnIncompleteDeployment implements ConfigValidator<ServiceSpec> {

  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
    if (!oldConfig.isPresent()) {
      return Collections.emptyList();
    }

    List<ConfigValidationError> errors = new ArrayList<>();
    if (!oldConfig.get().getRole().equals(newConfig.getRole())) {
      errors.add(ConfigValidationError.transitionError(
          "role",
          oldConfig.get().getRole(),
          newConfig.getRole(),
          "Detected service role change on an incomplete previous deployment!%n" +
          "Scheduler will not continue with deployment!%n" +
          "Resolve previous deployment issues before issuing role change.%n" +
          "Downgrade the service to the previous version if the issue persists.%n",
          true));
    }
    return errors;
  }
}
