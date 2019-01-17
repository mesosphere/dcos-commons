package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.Constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import java.util.Collection;
import java.util.Collections;

/**
 * This class represents a single port, with associated environment name.
 */
public class PortSpec extends DefaultResourceSpec {

  private final String envKey;

  private final String portName;

  private final DiscoveryInfo.Visibility visibility;

  private final Collection<String> networkNames;

  private final Collection<RangeSpec> ranges;

  @JsonCreator
  protected PortSpec(
      @JsonProperty("value") Protos.Value value,
      @JsonProperty("role") String role,
      @JsonProperty("pre-reserved-role") String preReservedRole,
      @JsonProperty("principal") String principal,
      @JsonProperty("env-key") String envKey,
      @JsonProperty("port-name") String portName,
      @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
      @JsonProperty("network-names") Collection<String> networkNames,
      @JsonProperty("ranges") Collection<RangeSpec> ranges)
  {
    super(Constants.PORTS_RESOURCE_TYPE, value, role, preReservedRole, principal);
    this.envKey = envKey;
    this.portName = portName;
    this.visibility = visibility;
    this.networkNames = networkNames;
    this.ranges = ranges;
  }

  public PortSpec(Builder builder) {
    this(
        builder.value,
        builder.role,
        builder.preReservedRole,
        builder.principal,
        builder.envKey,
        builder.portName,
        builder.visibility,
        builder.networkNames,
        builder.ranges);

    validatePort();
  }

  public static Builder newBuilder() {
    return new Builder();
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
        portSpec.getNetworkNames(),
        portSpec.getRanges());
  }

  protected void validatePort() {
    validateResource();
    ValidationUtils.nonEmpty(this, "portName", portName);
    ValidationUtils.nonNull(this, "visibility", visibility);
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

  @JsonProperty("ranges")
  public Collection<RangeSpec> getRanges() {
    return ranges;
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

  /**
   * {@link PortSpec} builder static inner class.
   */
  public static class Builder extends DefaultResourceSpec.Builder {

    protected String envKey;

    protected String portName;

    protected DiscoveryInfo.Visibility visibility;

    protected Collection<String> networkNames;

    protected Collection<RangeSpec> ranges = Collections.emptyList();

    protected Builder() {
    }

    public Builder envKey(String envKey) {
      this.envKey = envKey;
      return this;
    }

    public Builder portName(String portName) {
      this.portName = portName;
      return this;
    }

    public Builder visibility(DiscoveryInfo.Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    public Builder networkNames(Collection<String> networkNames) {
      this.networkNames = networkNames;
      return this;
    }

    public Builder ranges(Collection<RangeSpec> ranges) {
      this.ranges = ranges;
      return this;
    }

    public PortSpec build() {
      return new PortSpec(this);
    }
  }
}
