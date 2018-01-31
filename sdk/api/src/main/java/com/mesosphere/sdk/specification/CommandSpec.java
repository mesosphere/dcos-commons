package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Specification for defining a command.
 */
public interface CommandSpec {
    @JsonProperty("value")
    String getValue();

    @JsonProperty("environment")
    Map<String, String> getEnvironment();
}
