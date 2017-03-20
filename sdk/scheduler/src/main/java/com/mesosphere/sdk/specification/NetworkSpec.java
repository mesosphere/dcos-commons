package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;
import java.util.Set;

/**
 * Spec for defining a container's network membership.
 */
@JsonDeserialize(as = DefaultNetworkSpec.class)
public interface NetworkSpec {
    @JsonProperty("network-name")
    String getName();

    @JsonProperty("port-mappings")
    Map<Integer, Integer> getPortMappings();

    @JsonProperty("netgroups")
    Set<String> getNetgroups();
}
