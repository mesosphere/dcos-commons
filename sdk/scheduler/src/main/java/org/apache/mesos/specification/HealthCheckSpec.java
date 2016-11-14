package org.apache.mesos.specification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Duration;

/**
 * HealthCheck specification.
 */
@JsonDeserialize(as = DefaultHealthCheckSpec.class)
public interface HealthCheckSpec {
    String getName();
    String getCommand();
    Integer getMaxConsecutiveFailures();
    Duration getDelay();
    Duration getInterval();
    Duration getTimeout();
    Duration getGracePeriod();
}
