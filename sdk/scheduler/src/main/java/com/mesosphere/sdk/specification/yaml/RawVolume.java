package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Raw YAML volume.
 */
public final class RawVolume {

  private final String path;

  private final String type;

  private final List<String> profiles;

  private final int size;

  private RawVolume(
      @JsonProperty("path") String path,
      @JsonProperty("type") String type,
      @JsonProperty("profiles") List<String> profiles,
      @JsonProperty("size") int size)
  {
    this.path = path;
    this.type = type;
    this.profiles = profiles == null ? Collections.emptyList() : profiles;
    this.size = size;
  }

  public String getPath() {
    return path;
  }

  public String getType() {
    return type;
  }

  public List<String> getProfiles() {
    return profiles;
  }

  public int getSize() {
    return size;
  }
}
