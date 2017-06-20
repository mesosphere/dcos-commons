package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;
import java.util.Optional;

/**
 * This class represents a single port, with associated environment name.
 */
public class PortSpec extends DefaultResourceSpec {
    @NotNull
    @Size(min = 1)
    private final String portName;
    private final String envKey;
    private final Collection<String> networkNames;

    @JsonCreator
    public PortSpec(
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName,
            @JsonProperty("network-names") Collection<String> networkNames) {
        super(Constants.PORTS_RESOURCE_TYPE, value, role, preReservedRole, principal, envKey);
        this.portName = portName;
        this.envKey = envKey;
        this.networkNames = networkNames;
    }

    @JsonProperty("port-name")
    public String getPortName() {
        return portName;
    }

    @JsonProperty("env-key")
    @Override
    public Optional<String> getEnvKey() {
        return Optional.ofNullable(envKey);
    }

    @JsonIgnore
    public long getPort() {
        return getValue().getRanges().getRange(0).getBegin();
    }

    @Override
    public String toString() {
        return String.format(
                "name: %s, portName: %s, networkNames: %s, value: %s," +
                        " role: %s, preReservedRole: %s, principal: %s, envKey: %s",
                getName(),
                getPortName(),
                getNetworkNames(),
                TextFormat.shortDebugString(getValue()),
                getRole(),
                getPreReservedRole(),
                getPrincipal(),
                getEnvKey());
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public Collection<String> getNetworkNames() {
        return networkNames;
    }
}
