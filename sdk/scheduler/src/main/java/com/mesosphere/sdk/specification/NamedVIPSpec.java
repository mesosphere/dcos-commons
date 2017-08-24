package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;

/**
 * This class represents a port mapped to a DC/OS named VIP.
 */
public class NamedVIPSpec extends PortSpec {
    @NotNull
    @Size(min = 1)
    private final String protocol;
    @NotNull
    @Size(min = 1)
    private final String vipName;
    @NotNull
    private final Integer vipPort;

    @JsonCreator
    public NamedVIPSpec(
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
            @JsonProperty("vip-name") String vipName,
            @JsonProperty("vip-port") Integer vipPort,
            @JsonProperty("network-names") Collection<String> networkNames) {

        super(value, role, preReservedRole, principal, envKey, portName, visibility, networkNames);
        this.protocol = protocol;
        this.vipName = vipName;
        this.vipPort = vipPort;

        ValidationUtils.validate(this);
    }

    @JsonProperty("protocol")
    public String getProtocol() {
        return protocol;
    }

    @JsonProperty("vip-name")
    public String getVipName() {
        return vipName;
    }

    @JsonProperty("vip-port")
    public Integer getVipPort() {
        return vipPort;
    }

    @Override
    public String toString() {
        return String.format("%s, vip-name: %s, vip-port: %s, protocol: %s",
                super.toString(),
                getVipName(),
                getVipPort(),
                getProtocol());
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
