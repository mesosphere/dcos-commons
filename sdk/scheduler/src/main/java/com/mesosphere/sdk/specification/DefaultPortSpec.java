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
import java.util.Optional;

/**
 * The default implementation of {@link PortSpec}.
 */
public class DefaultPortSpec extends DefaultResourceSpec implements PortSpec {
    private final Optional<String> envKey;
    @NotNull
    @Size(min = 1)
    private final String portName;
    @NotNull
    private final DiscoveryInfo.Visibility visibility;
    private final Collection<String> networkNames;

    @JsonCreator
    public DefaultPortSpec(
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName,
            @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
            @JsonProperty("network-names") Collection<String> networkNames) {
        super(Constants.PORTS_RESOURCE_TYPE, value, role, preReservedRole, principal);
        this.envKey = Optional.ofNullable(envKey);
        this.portName = portName;
        if (visibility == null) {
            // TODO(nickbp): Remove this compatibility fallback after October 2017
            // Older SDK versions only have a visibility setting for VIPs, not ports. Default to visible.
            visibility = Constants.DISPLAYED_PORT_VISIBILITY;
        }
        this.visibility = visibility;
        this.networkNames = networkNames;
    }

    @Override
    @JsonProperty("port-name")
    public String getPortName() {
        return portName;
    }

    @Override
    @JsonProperty("visibility")
    public DiscoveryInfo.Visibility getVisibility() {
        return visibility;
    }

    @Override
    @JsonProperty("network-names")
    public Collection<String> getNetworkNames() {
        return networkNames;
    }

    @Override
    @JsonProperty("env-key")
    public Optional<String> getEnvKey() {
        return envKey;
    }

    @Override
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

    @Override
    public ResourceSpec getResourceSpec() {
        return this;
    }
}
