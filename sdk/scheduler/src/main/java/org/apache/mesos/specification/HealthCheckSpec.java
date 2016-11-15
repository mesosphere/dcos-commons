package org.apache.mesos.specification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * HealthCheck specification.
 */
@JsonDeserialize(as = DefaultHealthCheckSpec.class)
public interface HealthCheckSpec {
    String getName();

    String getCommand();

    Integer getMaxConsecutiveFailures();

    Integer getDelay();

    Integer getInterval();

    Integer getTimeout();

    Integer getGracePeriod();
}
