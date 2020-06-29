package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceLimits.
 */
public final class RawResourceLimits {
  private final String cpus;

  private final String memory;

  private RawResourceLimits(
      @JsonProperty("cpus") String cpus,
      @JsonProperty("memory") String memory)
  {
    this.cpus = cpus;
    this.memory = memory;
  }

  public String getCpus() {
    return cpus;
  }

  public String getMemory() {
    return memory;
  }
}
