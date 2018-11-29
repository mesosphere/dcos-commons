package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * A VolumeSpec defines the features of a Volume.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface VolumeSpec extends ResourceSpec {

  @JsonProperty("type")
  Type getType();

  @JsonProperty("container-path")
  String getContainerPath();

  @JsonProperty("profiles")
  List<String> getProfiles();

  /**
   * Returns a copy of the {@link VolumeSpec} which has been updated to have the provided disk size.
   */
  @JsonIgnore
  VolumeSpec withDiskSize(double diskSize);

  /**
   * Types of Volumes.
   */
  enum Type {
    ROOT,
    PATH,
    MOUNT
  }
}
