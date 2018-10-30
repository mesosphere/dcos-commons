package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.Constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class provides a default implementation of the VolumeSpec interface.
 */
public final class DefaultVolumeSpec extends DefaultResourceSpec implements VolumeSpec {

  /**
   * Disallow slashes in the container path. If a slash is used, Mesos will silently ignore the mount operation for
   * the persistent volume. See also:
   * mesos:src/slave/containerizer/mesos/isolators/filesystem/linux.cpp#L628
   * mesos:src/slave/containerizer/docker.cpp#L473
   * <p>
   * To play it safe, we explicitly whitelist the valid characters here.
   */
  private static final Pattern VALID_CONTAINER_PATH_PATTERN =
      Pattern.compile("[a-zA-Z0-9]+([a-zA-Z0-9_-]*)*");

  /**
   * Limit the length and characters in a profile name. A profile name should consist of alphanumeric
   * characters ([a-zA-Z0-9]), dashes (-), underscores (_) or dots(.), and should be non-empty and at most 128
   * characters. See: https://jira.mesosphere.com/browse/DCOS-40365
   */
  private static final Pattern VALID_PROFILE_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]{1,128}");

  private final Type type;

  private final String containerPath;

  private final List<String> profiles;

  @JsonCreator
  private DefaultVolumeSpec(
      @JsonProperty("type") Type type,
      @JsonProperty("container-path") String containerPath,
      @JsonProperty("profiles") List<String> profiles,
      @JsonProperty("name") String name,
      @JsonProperty("value") Protos.Value value,
      @JsonProperty("role") String role,
      @JsonProperty("pre-reserved-role") String preReservedRole,
      @JsonProperty("principal") String principal)
  {
    super(name, value, role, preReservedRole, principal);
    this.type = type;
    this.containerPath = containerPath;
    this.profiles = profiles == null ? Collections.emptyList() : profiles;

    validateVolume();
  }

  public static DefaultVolumeSpec createRootVolume(
      double diskSize,
      String containerPath,
      String role,
      String preReservedRole,
      String principal)
  {
    return new DefaultVolumeSpec(
        Type.ROOT,
        containerPath,
        Collections.emptyList(),
        Constants.DISK_RESOURCE_TYPE,
        scalarValue(diskSize),
        role,
        preReservedRole,
        principal);
  }

  public static DefaultVolumeSpec createMountVolume(
      double diskSize,
      String containerPath,
      List<String> profiles,
      String role,
      String preReservedRole,
      String principal)
  {
    return new DefaultVolumeSpec(
        Type.MOUNT,
        containerPath,
        profiles,
        Constants.DISK_RESOURCE_TYPE,
        scalarValue(diskSize),
        role,
        preReservedRole,
        principal);
  }

  private static Protos.Value scalarValue(double value) {
    Protos.Value.Builder builder = Protos.Value.newBuilder().setType(Protos.Value.Type.SCALAR);
    builder.getScalarBuilder().setValue(value);
    return builder.build();
  }

  @Override
  @JsonProperty("type")
  public Type getType() {
    return type;
  }

  @Override
  @JsonProperty("container-path")
  public String getContainerPath() {
    return containerPath;
  }

  @Override
  @JsonProperty("profiles")
  public List<String> getProfiles() {
    return profiles;
  }

  @Override
  public VolumeSpec withDiskSize(double diskSize) {
    return new DefaultVolumeSpec(
        type,
        containerPath,
        profiles,
        Constants.DISK_RESOURCE_TYPE,
        scalarValue(diskSize),
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

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  private void validateVolume() {
    validateResource();
    ValidationUtils.matchesRegex(
        this,
        "containerPath",
        containerPath,
        VALID_CONTAINER_PATH_PATTERN
    );

    ValidationUtils.nonNull(this, "profiles", profiles);
    if (type != Type.MOUNT) {
      ValidationUtils.isEmpty(this, "profiles", profiles);
    }

    int index = 0;
    for (String profile : profiles) {
      ValidationUtils.matchesRegex(this, "profiles[" + index + "]", profile, VALID_PROFILE_PATTERN);
      index++;
    }

    ValidationUtils.isUnique(this, "profiles", profiles.stream());
  }
}
