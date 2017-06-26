package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;

/**
 * This class represents a ports resource with multiple ports.
 */
public class PortsSpec implements ResourceSpec {
    @NotNull
    @Size(min = 1)
    private final String name;
    @NotNull
    private final Protos.Value value;
    @NotNull
    @Size(min = 1)
    private final Collection<PortSpec> portSpecs;
    @NotNull
    @Size(min = 1)
    private final String role;
    @NotNull
    @Size(min = 1)
    private final String principal;
    private final String envKey;

    @JsonCreator
    public PortsSpec(
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-specs") Collection<PortSpec> portSpecs) {
        this.name = name;
        this.value = value;
        this.portSpecs = portSpecs;
        this.role = role;
        this.principal = principal;
        this.envKey = envKey;

        ValidationUtils.validate(this);
    }

    @JsonProperty("port-specs")
    public Collection<PortSpec> getPortSpecs() {
        return portSpecs;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public Protos.Value getValue() {
        return value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getPreReservedRole() {
        return Constants.ANY_ROLE;
    }

    @Override
    public String getPrincipal() {
        return principal;
    }
}
