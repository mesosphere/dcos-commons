package org.apache.mesos.specification;

import java.time.Duration;

/**
 * HealthCheck specification.
 */
public interface HealthCheckSpec {
    String getCommand();
    Integer getMaxConsecutiveFailures();
    Duration getDelay();
    Duration getInterval();
    Duration getTimeout();
    Duration getGracePeriod();
}
