package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.HealthCheckSpec;

/**
 * Created by gabriel on 11/8/16.
 */
public class HealthCheckUtils {
    public static Protos.HealthCheck getHealthCheck(HealthCheckSpec healthCheckSpec) {
        return Protos.HealthCheck.newBuilder()
                .setDelaySeconds(healthCheckSpec.getDelay().getSeconds())
                .setIntervalSeconds(healthCheckSpec.getInterval().getSeconds())
                .setTimeoutSeconds(healthCheckSpec.getTimeout().getSeconds())
                .setConsecutiveFailures(healthCheckSpec.getMaxConsecutiveFailures())
                .setGracePeriodSeconds(healthCheckSpec.getGracePeriod().getSeconds())
                .build();
    }
}
