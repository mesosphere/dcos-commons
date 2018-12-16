package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ServiceSpec;

import javax.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * A {@link TLSRequiresServiceAccount} checks whether the configuration contains provisioning of TLS
 * artifacts and whether the provided {@link SchedulerConfig} contains a service account.
 */
public class TLSRequiresServiceAccount implements ConfigValidator<ServiceSpec> {

  private final SchedulerConfig schedulerConfig;

  TLSRequiresServiceAccount(@NotNull SchedulerConfig schedulerConfig) {
    this.schedulerConfig = schedulerConfig;
  }

  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
    if (TaskUtils.hasTasksWithTLS(newConfig)) {
      try {
        // Just check that construction succeeds.
        schedulerConfig.getDcosAuthTokenProvider();
      } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
        return Collections.singletonList(ConfigValidationError.valueError(
            "transport-encryption",
            "",
            "Scheduler is missing a service account that is required for " +
                "provisioning TLS artifacts. Please configure in order to continue."));
      }
    }
    return Collections.emptyList();
  }
}
