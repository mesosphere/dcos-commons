package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * HealthCheck specification.
 */
@JsonDeserialize(as = DefaultHealthCheckSpec.class)
public interface HealthCheckSpec {
    String getCommand();

    Integer getMaxConsecutiveFailures();

    Integer getDelay();

    Integer getInterval();

    Integer getTimeout();

    Integer getGracePeriod();
}
