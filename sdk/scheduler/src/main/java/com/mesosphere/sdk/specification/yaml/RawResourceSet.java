package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceSet.
 */
public final class RawResourceSet {

  private final Double cpus;

  private final Double gpus;

  private final Integer memory;

  private final WriteOnceLinkedHashMap<String, RawPort> ports;

  private final RawVolume volume;

  private final WriteOnceLinkedHashMap<String, RawVolume> volumes;

  private final RawResourceLimits resourceLimits;

  private RawResourceSet(
      @JsonProperty("cpus") Double cpus,
      @JsonProperty("gpus") Double gpus,
      @JsonProperty("memory") Integer memory,
      @JsonProperty("resource-limits") RawResourceLimits resourceLimits,
      @JsonProperty("ports") WriteOnceLinkedHashMap<String, RawPort> ports,
      @JsonProperty("volume") RawVolume volume,
      @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes)
  {
    this.cpus = cpus;
    this.gpus = gpus;
    this.memory = memory;
    this.ports = ports;
    this.volume = volume;
    this.volumes = volumes;
    this.resourceLimits = resourceLimits;
  }

  public Double getCpus() {
    return cpus;
  }

  public Double getGpus() {
    return gpus;
  }

  public Integer getMemory() {
    return memory;
  }

  public WriteOnceLinkedHashMap<String, RawPort> getPorts() {
    return ports;
  }

  public RawVolume getVolume() {
    return volume;
  }

  public WriteOnceLinkedHashMap<String, RawVolume> getVolumes() {
    return volumes;
  }

  public RawResourceLimits getResourceLimits() { return resourceLimits; }
}
