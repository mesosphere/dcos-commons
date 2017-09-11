package com.mesosphere.sdk.config.validate;

import java.util.Arrays;
import java.util.Collection;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.ServiceSpec;

/**
 * Returns a collection {@link ConfigValidator}s to be enabled in services by default
 */
public class DefaultConfigValidators {

    private DefaultConfigValidators() {
        // do not instantiate
    }

    public static Collection<ConfigValidator<ServiceSpec>> getValidators(SchedulerFlags schedulerFlags) {
        return Arrays.asList(
                new ServiceNameCannotContainDoubleUnderscores(),
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange(),
                new PodSpecsCannotChangeNetworkRegime(),
                new PreReservationCannotChange(),
                new UserCannotChange(),
                new TLSRequiresServiceAccount(schedulerFlags));
    }
}
