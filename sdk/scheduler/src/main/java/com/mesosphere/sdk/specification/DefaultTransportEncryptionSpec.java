package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Optional;

/**
 * Default implementation of {@link TransportEncryptionSpec}.
 */
public class DefaultTransportEncryptionSpec implements TransportEncryptionSpec {

  private final String name;

  private final Type type;

  private final Provisioning provisioning;

  private final Optional<String> secret;

  private final Optional<String> mountPath;

  @JsonCreator
  private DefaultTransportEncryptionSpec(
      @JsonProperty("name") String name,
      @JsonProperty("type") Type type,
      @JsonProperty("secret") Optional<String> secret,
      @JsonProperty("mount-path") Optional<String> mountPath,
      @JsonProperty("provisinoing") Provisioning provisioning)
  {
    this.name = name;
    this.type = type;
    this.secret = secret;
    this.mountPath = mountPath;
    this.provisioning = provisioning;
  }

  public DefaultTransportEncryptionSpec(Builder builder) {
    this(builder.name, builder.type, builder.secret, builder.mountPath, builder.provisioning);
    ValidationUtils.nonBlank(this, "name", name);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public Provisioning getProvisioning() {
    return provisioning;
  }

  @Override
  public Optional<String> getSecret() {
    return secret;
  }

  @Override
  public Optional<String> getMountPath() {
    return mountPath;
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
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

  /**
   * A {@link DefaultTransportEncryptionSpec} builder.
   */
  public static final class Builder {
    private String name;

    private Type type;

    private Provisioning provisioning;

    private Optional<String> secret;

    private Optional<String> mountPath;

    private Builder() {
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder type(Type type) {
      this.type = type;
      return this;
    }

    public Builder provisioning(Provisioning provisioning) {
      this.provisioning = provisioning;
      return this;
    }

    public Builder secret(Optional<String> secret) {
      this.secret = secret;
      return this;
    }

    public Builder mountPath(Optional<String> mountPath) {
      this.mountPath = mountPath;
      return this;
    }

    public DefaultTransportEncryptionSpec build() {
      return new DefaultTransportEncryptionSpec(this);
    }
  }
}
