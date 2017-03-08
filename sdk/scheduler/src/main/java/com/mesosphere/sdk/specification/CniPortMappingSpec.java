package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public interface CniPortMappingSpec {
    @JsonProperty("cni-port-mappings")
    Map<Integer, Integer> getPortMappings();
}
