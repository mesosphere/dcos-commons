package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Spec for defining a container's network membership.
 */
public interface NetworkSpec {
    @JsonProperty("network-name")
    String getName();

    @JsonProperty("port-mappings")
    Map<Integer, Integer> getPortMappings();

    @JsonProperty("network-labels")
    Map<String, String> getLabels();
}
