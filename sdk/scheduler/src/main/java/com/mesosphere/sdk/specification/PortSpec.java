package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;

/**
 * This class represents a single port, with associated environment name.
 */
public class PortSpec extends DefaultResourceSpec {
    private final String envKey;
    @NotNull
    @Size(min = 1)
    private final String portName;
    @NotNull
    private final DiscoveryInfo.Visibility visibility;
    private final Collection<String> networkNames;

    @JsonCreator
    public PortSpec(
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName,
            @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
            @JsonProperty("network-names") Collection<String> networkNames) {
        super(Constants.PORTS_RESOURCE_TYPE, value, role, preReservedRole, principal);
        this.envKey = envKey;
        this.portName = portName;
        this.visibility = visibility;
        this.networkNames = networkNames;
    }

    /**
     * Returns a copy of the provided {@link PortSpec} which has been updated to have the provided {@code value}.
     */
    @JsonIgnore
    public static PortSpec withValue(PortSpec portSpec, Protos.Value value) {
        return new PortSpec(
                value,
                portSpec.getRole(),
                portSpec.getPreReservedRole(),
                portSpec.getPrincipal(),
                portSpec.getEnvKey(),
                portSpec.getPortName(),
                portSpec.getVisibility(),
                portSpec.getNetworkNames());
    }

    @JsonProperty("port-name")
    public String getPortName() {
        return portName;
    }

    @JsonProperty("visibility")
    public DiscoveryInfo.Visibility getVisibility() {
        return visibility;
    }

    @JsonProperty("network-names")
    public Collection<String> getNetworkNames() {
        return networkNames;
    }

    @JsonProperty("env-key")
    public String getEnvKey() {
        return envKey;
    }

    @JsonIgnore
    public long getPort() {
        return getValue().getRanges().getRange(0).getBegin();
    }

    @Override
    public String toString() {
        return String.format("%s, port-name: %s, network-names: %s, env-key: %s, visibility: %s",
                super.toString(),
                getPortName(),
                getNetworkNames(),
                getEnvKey(),
                getVisibility());
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
