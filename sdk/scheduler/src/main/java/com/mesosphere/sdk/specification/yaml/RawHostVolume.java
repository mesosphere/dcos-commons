package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RawHostVolume YAML spec.
 */
public final class RawHostVolume {
  private final String hostPath;

  private final String containerPath;

  private final String mode;

  private RawHostVolume(
      @JsonProperty("host-path") String hostPath,
      @JsonProperty("container-path") String containerPath,
      @JsonProperty("mode") String mode)
  {
    this.hostPath = hostPath;
    this.containerPath = containerPath;
    this.mode = mode;
  }

  public String getMode() {
    return mode;
  }

  public String getHostPath() {
    return hostPath;
  }

  public String getContainerPath() {
    return containerPath;
  }

}
