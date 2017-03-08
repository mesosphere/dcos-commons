package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Default implementation of CniPortMappingSpec
 */
public class DefaultCniPortMappingSpec implements CniPortMappingSpec{
    @NotNull
    private final Map<Integer, Integer> portMappings;  // Key: Host port, Value: container port

    @JsonCreator
    public DefaultCniPortMappingSpec(
            @JsonProperty("port-mappings") Map<Integer, Integer> portMappings) {
        this.portMappings = portMappings;
    }

    public Map<Integer, Integer> getPortMappings() { return portMappings; }

    @Override
    public boolean equals(Object other) { return EqualsBuilder.reflectionEquals(this, other); }
}
