package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Default implementation of {@link VipSpec}.
 */
public final class DefaultVipSpec implements VipSpec {

  private final Integer applicationPort;

  private final String vipName;

  private final Integer vipPort;

  private DefaultVipSpec(
      @JsonProperty("application-port") Integer applicationPort,
      @JsonProperty("vip-name") String vipName,
      @JsonProperty("vip-port") Integer vipPort)
  {
    this.applicationPort = applicationPort;
    this.vipName = vipName;
    this.vipPort = vipPort;
  }

  private DefaultVipSpec(Builder builder) {
    this(builder.applicationPort, builder.vipName, builder.vipPort);
    ValidationUtils.nonNegative(this, "applicationPort", applicationPort);
    ValidationUtils.nonEmpty(this, "vipName", vipName);
    ValidationUtils.nonNegative(this, "vipPort", vipPort);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(DefaultVipSpec copy) {
    Builder builder = new Builder();
    builder.applicationPort = copy.applicationPort;
    builder.vipName = copy.vipName;
    builder.vipPort = copy.vipPort;
    return builder;
  }

  @Override
  @JsonProperty("application-port")
  public int getApplicationPort() {
    return applicationPort;
  }

  @Override
  @JsonProperty("vip-name")
  public String getVipName() {
    return vipName;
  }

  @Override
  @JsonProperty("vip-port")
  public int getVipPort() {
    return vipPort;
  }


  /**
   * {@code DefaultVipSpec} builder static inner class.
   */
  public static final class Builder {
    private int applicationPort;

    private String vipName;

    private int vipPort;

    private Builder() {
    }

    /**
     * Sets the {@code applicationPort} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param applicationPort the {@code applicationPort} to set
     * @return a reference to this Builder
     */
    public Builder applicationPort(int applicationPort) {
      this.applicationPort = applicationPort;
      return this;
    }

    /**
     * Sets the {@code vipName} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param vipName the {@code vipName} to set
     * @return a reference to this Builder
     */
    public Builder vipName(String vipName) {
      this.vipName = vipName;
      return this;
    }

    /**
     * Sets the {@code vipPort} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param vipPort the {@code vipPort} to set
     * @return a reference to this Builder
     */
    public Builder vipPort(int vipPort) {
      this.vipPort = vipPort;
      return this;
    }

    /**
     * Returns a {@code DefaultVipSpec} built from the parameters previously set.
     *
     * @return a {@code DefaultVipSpec} built with parameters of this {@code DefaultVipSpec.Builder}
     */
    public DefaultVipSpec build() {
      return new DefaultVipSpec(this);
    }
  }
}
