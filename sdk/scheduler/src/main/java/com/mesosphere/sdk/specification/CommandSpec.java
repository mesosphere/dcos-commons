package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

/**
 * Specification for defining a command.
 */
@JsonDeserialize(as = DefaultCommandSpec.class)
public interface CommandSpec {
    @JsonProperty("value")
    String getValue();

    @JsonProperty("environment")
    Map<String, String> getEnvironment();
}
