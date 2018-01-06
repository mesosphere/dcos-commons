package com.mesosphere.sdk.config.validate;

import java.util.Arrays;
import java.util.Collection;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ServiceSpec;

/**
 * Catalog of {@link ConfigValidator}s to be enabled in services by default.
 */
public class DefaultConfigValidators {

    private DefaultConfigValidators() {
        // do not instantiate
    }

    public static Collection<ConfigValidator<ServiceSpec>> getValidators(SchedulerConfig schedulerConfig) {
        return Arrays.asList(
                new ServiceNameCannotContainDoubleUnderscores(),
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange(),
                new PodSpecsCannotUseUnsupportedFeatures(),
                new PodSpecsCannotChangeNetworkRegime(),
                new PreReservationCannotChange(),
                new UserCannotChange(),
                new TLSRequiresServiceAccount(schedulerConfig),
                new DomainCapabilityValidator());
    }
}
