package com.mesosphere.sdk.specification;

/**
 * HealthCheck specification.
 */
public interface HealthCheckSpec extends ReadinessCheckSpec {
    Integer getGracePeriod();

    Integer getMaxConsecutiveFailures();
}
