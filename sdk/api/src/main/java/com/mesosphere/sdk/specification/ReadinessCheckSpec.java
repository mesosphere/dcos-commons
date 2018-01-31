package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ReadinessCheck specification.
 */
public interface ReadinessCheckSpec {
    @JsonProperty("command")
    String getCommand();

    @JsonProperty("delay")
    Integer getDelay();

    @JsonProperty("interval")
    Integer getInterval();

    @JsonProperty("timeout")
    Integer getTimeout();
}
