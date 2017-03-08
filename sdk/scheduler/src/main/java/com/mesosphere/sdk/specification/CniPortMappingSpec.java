package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface CniPortMappingSpec {
    @JsonProperty("port-mappings")
    Map<Integer, Integer> getPortMappings();
}
