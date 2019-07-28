package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Catalog of {@link ConfigValidator}s to be enabled in services by default.
 */
public final class DefaultConfigValidators {

  private DefaultConfigValidators() {
  }

  public static Collection<ConfigValidator<ServiceSpec>> getValidators(
      SchedulerConfig schedulerConfig)
  {
    return Arrays.asList(
        new ServiceNameCannotContainDoubleUnderscores(),
        new PodSpecsCannotShrink(),
        new PodSpecsCannotUseUnsupportedFeatures(),
        new PodSpecsCannotChangeNetworkRegime(),
        new PreReservationCannotChange(),
        new UserCannotChange(),
        new TLSRequiresServiceAccount(schedulerConfig),
        new DomainCapabilityValidator(),
        new PlacementRuleIsValid(),
        new RegionCannotChange(),
        new ServiceNameCannotBreakDNS(),
        new TaskSpecsCannotUseUnsupportedFeatures());
  }

  public static Collection<ConfigValidator<ServiceSpec>> getRoleValidators(boolean hasRoleChanged,
      boolean hasCompletedDeployment)
  {
    //Conditionally include addtional validators here for role related changes.
    if (hasRoleChanged && hasCompletedDeployment) {
      return Collections.emptyList();
    } else if (hasRoleChanged && !hasCompletedDeployment) {
      return Arrays.asList(new ServiceRoleCannotChangeOnIncompleteDeployment());
    } else {
      return Arrays.asList(new TaskVolumesCannotChange());
    }
  }
}
