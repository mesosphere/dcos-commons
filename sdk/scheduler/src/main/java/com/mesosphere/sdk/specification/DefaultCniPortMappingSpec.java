package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Default implementation of CniPortMappingSpec
 */
public class DefaultCniPortMappingSpec implements CniPortMappingSpec{
    // TODO annotations?
    private final Map<Integer, Integer> portMappings;  // Key: Host port, Value: container port

    @JsonCreator
    public DefaultCniPortMappingSpec(
            @JsonProperty("port-mappings") Map<Integer, Integer> portMappings) {
        this.portMappings = portMappings;
    }

    public Map<Integer, Integer> getPortMappings() { return portMappings; }
}
