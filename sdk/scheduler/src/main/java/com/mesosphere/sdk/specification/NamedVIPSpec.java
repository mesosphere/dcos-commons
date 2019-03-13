package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import java.util.Collection;
import java.util.Collections;


/**
 * This class represents a port mapped to a DC/OS named VIP.
 */
public final class NamedVIPSpec extends PortSpec {

  private final String protocol;

  private final String vipName;

  private final Integer vipPort;

  @JsonCreator
  private NamedVIPSpec(
      @JsonProperty("value") Protos.Value value,
      @JsonProperty("role") String role,
      @JsonProperty("pre-reserved-role") String preReservedRole,
      @JsonProperty("principal") String principal,
      @JsonProperty("env-key") String envKey,
      @JsonProperty("port-name") String portName,
      @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
      @JsonProperty("network-names") Collection<String> networkNames,
      @JsonProperty("protocol") String protocol,
      @JsonProperty("vip-name") String vipName,
      @JsonProperty("vip-port") Integer vipPort)
  {
    super(
        value,
        role,
        preReservedRole,
        principal,
        envKey,
        portName,
        visibility,
        networkNames,
        Collections.emptyList());
    this.protocol = protocol;
    this.vipName = vipName;
    this.vipPort = vipPort;
  }

  private NamedVIPSpec(Builder builder) {
    this(
        builder.value,
        builder.role,
        builder.preReservedRole,
        builder.principal,
        builder.envKey,
        builder.portName,
        builder.visibility,
        builder.networkNames,
        builder.protocol,
        builder.vipName,
        builder.vipPort);

    validatePort();
    ValidationUtils.nonEmpty(this, "protocol", protocol);
    ValidationUtils.nonEmpty(this, "vipName", vipName);
    ValidationUtils.nonNull(this, "vipPort", vipPort);
  }

  public static Builder newBuilder() {
    return new Builder();
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

  /**
   * {@link NamedVIPSpec} builder static inner class.
   */
  public static final class Builder extends PortSpec.Builder {

    private String protocol;

    private String vipName;

    private Integer vipPort;

    private Builder() {
    }

    public Builder protocol(String protocol) {
      this.protocol = protocol;
      return this;
    }

    public Builder vipName(String vipName) {
      this.vipName = vipName;
      return this;
    }

    public Builder vipPort(Integer vipPort) {
      this.vipPort = vipPort;
      return this;
    }

    public NamedVIPSpec build() {
      PortSpec portSpec = super.build();
      return new NamedVIPSpec(
          portSpec.getValue(),
          portSpec.getRole(),
          portSpec.getPreReservedRole(),
          portSpec.getPrincipal(),
          portSpec.getEnvKey(),
          portSpec.getPortName(),
          portSpec.getVisibility(),
          portSpec.getNetworkNames(),
          protocol,
          vipName,
          vipPort);
    }
  }
}
