package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.regex.Pattern;

/**
 * Default implementation of {@link HostVolumeSpec}.
 */
public final class DefaultHostVolumeSpec implements HostVolumeSpec {
  /**
   * Regexp for valid containerPath:
   * No leading slash character is allowed, but we allow more slashes.
   */
  private static final Pattern VALID_CONTAINER_PATH_PATTERN =
      Pattern.compile("([.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)");

  /**
   * Regexp for valid hostPath:
   * Require a leading slash character.
   */
  private static final Pattern VALID_HOST_PATH_PATTERN =
      Pattern.compile("(/[.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)");

  private final String hostPath;

  private final String containerPath;

  @JsonCreator
  private DefaultHostVolumeSpec(
      @JsonProperty("host-path") String hostPath,
      @JsonProperty("container-path") String containerPath)
  {
    this.hostPath = hostPath;
    this.containerPath = containerPath;
  }

  private DefaultHostVolumeSpec(Builder builder) {
    this(builder.hostPath, builder.containerPath);

    ValidationUtils.matchesRegex(this, "host-path", hostPath, VALID_HOST_PATH_PATTERN);
    ValidationUtils
        .matchesRegex(this, "container-path", containerPath, VALID_CONTAINER_PATH_PATTERN);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @JsonProperty("host-path")
  @Override
  public String getHostPath() {
    return hostPath;
  }

  @JsonProperty("container-path")
  @Override
  public String getContainerPath() {
    return containerPath;
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

  /**
   * {@link DefaultSecretSpec} builder static inner class.
   */
  public static final class Builder {

    private String hostPath;

    private String containerPath;

    private Builder() {
    }

    public Builder hostPath(String hostPath) {
      this.hostPath = hostPath;
      return this;
    }

    public Builder containerPath(String containerPath) {
      this.containerPath = containerPath;
      return this;
    }

    public DefaultHostVolumeSpec build() {
      return new DefaultHostVolumeSpec(this);
    }
  }
}
