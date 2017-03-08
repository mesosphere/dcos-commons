package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * Default implementation of {@link NetworkSpec}.
 */
public class DefaultNetworkSpec implements NetworkSpec {
    @Valid
    private Map<Integer, Integer> portMappings;

    @JsonCreator
    public DefaultNetworkSpec(
            @JsonProperty("port-mapping") Map<Integer, Integer> portMapings) {
        this.portMappings = portMapings;
    }
    @Override
    public Map<Integer, Integer> getPortMappings() { return portMappings; }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
