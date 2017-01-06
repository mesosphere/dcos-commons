package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * ReadinessCheck specification.
 */
@JsonDeserialize(as = DefaultReadinessCheckSpec.class)
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
