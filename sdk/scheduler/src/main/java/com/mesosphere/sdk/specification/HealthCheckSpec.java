package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * HealthCheck specification.
 */
@JsonDeserialize(as = DefaultHealthCheckSpec.class)
public interface HealthCheckSpec extends ReadinessCheckSpec {
    Integer getGracePeriod();

    Integer getMaxConsecutiveFailures();
}
