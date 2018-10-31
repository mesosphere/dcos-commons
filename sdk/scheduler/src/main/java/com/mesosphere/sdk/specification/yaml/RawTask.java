package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Raw YAML task.
 */
public final class RawTask {

  private final String goal;

  private final Boolean essential;

  private final String cmd;

  private final String labelsCsv;

  private final Map<String, String> env;

  private final WriteOnceLinkedHashMap<String, RawConfig> configs;

  private final Double cpus;

  private final Double gpus;

  private final Integer memory;

  private final WriteOnceLinkedHashMap<String, RawPort> ports;

  private final RawHealthCheck healthCheck;

  private final RawReadinessCheck readinessCheck;

  private final RawVolume volume;

  private final WriteOnceLinkedHashMap<String, RawVolume> volumes;

  private final String resourceSet;

  private final RawDiscovery discovery;

  private final Integer taskKillGracePeriodSeconds;

  private final List<RawTransportEncryption> transportEncryption;

  private RawTask(
      @JsonProperty("goal") String goal,
      @JsonProperty("essential") Boolean essential,
      @JsonProperty("cmd") String cmd,
      @JsonProperty("labels") String labels,
      @JsonProperty("env") Map<String, String> env,
      @JsonProperty("configs") WriteOnceLinkedHashMap<String, RawConfig> configs,
      @JsonProperty("cpus") Double cpus,
      @JsonProperty("gpus") Double gpus,
      @JsonProperty("memory") Integer memory,
      @JsonProperty("ports") WriteOnceLinkedHashMap<String, RawPort> ports,
      @JsonProperty("health-check") RawHealthCheck healthCheck,
      @JsonProperty("readiness-check") RawReadinessCheck readinessCheck,
      @JsonProperty("volume") RawVolume volume,
      @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes,
      @JsonProperty("resource-set") String resourceSet,
      @JsonProperty("discovery") RawDiscovery discovery,
      @JsonProperty("kill-grace-period") Integer taskKillGracePeriodSeconds,
      @JsonProperty("transport-encryption") List<RawTransportEncryption> transportEncryption)
  {
    this.goal = goal;
    this.essential = essential;
    this.cmd = cmd;
    this.labelsCsv = labels;
    this.env = env;
    this.configs = configs;
    this.cpus = cpus;
    this.gpus = gpus;
    this.memory = memory;
    this.ports = ports;
    this.healthCheck = healthCheck;
    this.readinessCheck = readinessCheck;
    this.volume = volume;
    this.volumes = volumes;
    this.resourceSet = resourceSet;
    this.discovery = discovery;
    this.taskKillGracePeriodSeconds = taskKillGracePeriodSeconds;
    this.transportEncryption = transportEncryption;
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

  public String getResourceSet() {
    return resourceSet;
  }

  public RawDiscovery getDiscovery() {
    return discovery;
  }

  public Integer getTaskKillGracePeriodSeconds() {
    return taskKillGracePeriodSeconds;
  }

  public RawHealthCheck getHealthCheck() {
    return healthCheck;
  }

  public RawReadinessCheck getReadinessCheck() {
    return readinessCheck;
  }

  public String getGoal() {
    return goal;
  }

  public Boolean isEssential() {
    return essential;
  }

  public String getCmd() {
    return cmd;
  }

  public String getLabelsCsv() {
    return labelsCsv;
  }

  public List<String[]> getValidatedLabels() throws IllegalArgumentException {
    List<String[]> kvs = Arrays.stream(labelsCsv.split(","))
        .map(s -> s.split(":", 2))
        .collect(Collectors.toList());
    kvs.forEach(kv -> {
      if (kv.length != 2) {
        throw new IllegalArgumentException(String.format(
            "Illegal label string, got %s, should be " +
                "comma-seperated key value pairs (seperated by colons)." +
                " For example: k_0:v_0,k_1:v_1,...,k_n:v_n",
            labelsCsv
        ));
      }
    });
    return kvs;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public WriteOnceLinkedHashMap<String, RawPort> getPorts() {
    return ports;
  }

  public WriteOnceLinkedHashMap<String, RawConfig> getConfigs() {
    return configs;
  }

  public RawVolume getVolume() {
    return volume;
  }

  public WriteOnceLinkedHashMap<String, RawVolume> getVolumes() {
    return volumes;
  }

  public List<RawTransportEncryption> getTransportEncryption() {
    return transportEncryption == null ?
        Collections.emptyList() : transportEncryption;
  }
}
