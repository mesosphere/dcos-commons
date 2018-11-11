package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.Constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.TextFormat;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

/**
 * This class provides a default implementation of the ResourceSpec interface.
 */
public class DefaultResourceSpec implements ResourceSpec {

  private final String name;

  private final Protos.Value value;

  private final String role;

  private final String principal;

  private final String preReservedRole;

  @JsonCreator
  protected DefaultResourceSpec(
      @JsonProperty("name") String name,
      @JsonProperty("value") Protos.Value value,
      @JsonProperty("role") String role,
      @JsonProperty("pre-reserved-role") String preReservedRole,
      @JsonProperty("principal") String principal)
  {
    this.name = name;
    this.value = value;
    this.role = role;
    this.preReservedRole = preReservedRole == null ? Constants.ANY_ROLE : preReservedRole;
    this.principal = principal;
  }

  private DefaultResourceSpec(Builder builder) {
    this(builder.name, builder.value, builder.role, builder.preReservedRole, builder.principal);

    validateResource();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(ResourceSpec copy) {
    Builder builder = new Builder();
    builder.name = copy.getName();
    builder.value = copy.getValue();
    builder.role = copy.getRole();
    builder.preReservedRole = copy.getPreReservedRole();
    builder.principal = copy.getPrincipal();
    return builder;
  }

  protected void validateResource() {
    ValidationUtils.nonEmpty(this, "name", name);
    ValidationUtils.nonNull(this, "value", value);
    ValidationUtils.nonEmpty(this, "role", role);
    ValidationUtils.nonEmpty(this, "principal", principal);

    if (value.hasScalar()) {
      if (value.getScalar().getValue() <= 0) {
        throw new IllegalArgumentException(
            String.format("Scalar resource value must be greater than zero: %s", this));
      }
    } else if (!value.hasRanges()) {
      throw new IllegalArgumentException(
          String.format("Expected resource value to be a scalar or range: %s", this));
    }
  }

  @Override
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @Override
  @JsonProperty("value")
  public Protos.Value getValue() {
    return value;
  }

  @Override
  @JsonProperty("role")
  public String getRole() {
    return role;
  }

  @Override
  @JsonProperty("pre-reserved-role")
  public String getPreReservedRole() {
    return preReservedRole;
  }

  @Override
  @JsonProperty("principal")
  public String getPrincipal() {
    return principal;
  }

  @Override
  public String toString() {
    return String.format(
        "name: %s, value: %s, role: %s, pre-reserved-role: %s, principal: %s",
        getName(),
        TextFormat.shortDebugString(getValue()),
        getRole(),
        getPreReservedRole(),
        getPrincipal());
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
   * {@link DefaultResourceSpec} builder static inner class.
   */
  public static class Builder {
    protected String name;

    protected Protos.Value value;

    protected String role;

    protected String principal;

    protected String preReservedRole = Constants.ANY_ROLE;

    protected Builder() {
    }

    /**
     * Sets the {@code name} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param name the {@code name} to set
     * @return a reference to this Builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the {@code value} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param value the {@code value} to set
     * @return a reference to this Builder
     */
    public Builder value(Protos.Value value) {
      this.value = value;
      return this;
    }

    /**
     * Sets the {@code role} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param role the {@code role} to set
     * @return a reference to this Builder
     */
    public Builder role(String role) {
      this.role = role;
      return this;
    }

    public Builder preReservedRole(String preReservedRole) {
      this.preReservedRole = preReservedRole;
      return this;
    }

    /**
     * Sets the {@code principal} and returns a reference to this Builder so that the methods can be chained
     * together.
     *
     * @param principal the {@code principal} to set
     * @return a reference to this Builder
     */
    public Builder principal(String principal) {
      this.principal = principal;
      return this;
    }

    /**
     * Returns a {@code DefaultResourceSpec} built from the parameters previously set.
     *
     * @return a {@code DefaultResourceSpec} built with parameters of this
     * {@code DefaultResourceSpec.Builder}
     */
    public DefaultResourceSpec build() {
      return new DefaultResourceSpec(this);
    }
  }
}
