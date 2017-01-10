package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * HealthCheck specification.
 */
@JsonDeserialize(as = DefaultHealthCheckSpec.class)
public interface HealthCheckSpec {
    @JsonProperty("command")
    String getCommand();

    @JsonProperty("max-consecutive-failures")
    Integer getMaxConsecutiveFailures();

    @JsonProperty("delay")
    Integer getDelay();

    @JsonProperty("interval")
    Integer getInterval();

    @JsonProperty("timeout")
    Integer getTimeout();

    @JsonProperty("grace-period")
    Integer getGracePeriod();
}
