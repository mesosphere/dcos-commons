package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RawHostVolume YAML spec.
 */
public final class RawExternalVolume {

  private final String containerPath;

  private final String mode;

  private final int size;

  private RawExternalVolume(
          @JsonProperty("size") int size,
          @JsonProperty("host-path") String hostPath,
      @JsonProperty("container-path") String containerPath,
      @JsonProperty("mode") String mode)
  {
    this.size = size;
    this.containerPath = containerPath;
    this.mode = mode;
  }

  public int getSize() {
    return size;
  }

  public String getMode() {
    return mode;
  }

  public String getContainerPath() {
    return containerPath;
  }

}
