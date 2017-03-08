package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * Default implementation of {@link NetworkSpec}.
 */
public class DefaultNetworkSpec implements NetworkSpec {
    @NotNull
    @Size(min = 1)
    private String name;
    private CniPortMappingSpec cniPortMappingSpec;

    @JsonCreator
    public DefaultNetworkSpec(
            @JsonProperty("name") String name,
            @JsonProperty("port-mapping") CniPortMappingSpec portMapings) {
        this.name               = name;
        this.cniPortMappingSpec = portMapings;
    }

    @Override
    public String getName() { return name; }

    @Override
    public CniPortMappingSpec getCniPortMappingSpec() { return cniPortMappingSpec; }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
