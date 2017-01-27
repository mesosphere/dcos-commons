package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Raw YAML task.
 */
public class RawTask {

    private final String goal;
    private final String cmd;
    private final Map<String, String> env;
    private final WriteOnceLinkedHashMap<String, RawConfig> configs;
    private final Double cpus;
    private final Integer memory;
    private final WriteOnceLinkedHashMap<String, RawPort> ports;
    private final RawHealthCheck healthCheck;
    private final RawReadinessCheck readinessCheck;
    private final RawVolume volume;
    private final WriteOnceLinkedHashMap<String, RawVolume> volumes;
    private final String resourceSet;

    private RawTask(
            @JsonProperty("goal") String goal,
            @JsonProperty("cmd") String cmd,
            @JsonProperty("env") Map<String, String> env,
            @JsonProperty("configs") WriteOnceLinkedHashMap<String, RawConfig> configs,
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("ports") WriteOnceLinkedHashMap<String, RawPort> ports,
            @JsonProperty("health-check") RawHealthCheck healthCheck,
            @JsonProperty("readiness-check") RawReadinessCheck readinessCheck,
            @JsonProperty("volume") RawVolume volume,
            @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes,
            @JsonProperty("resource-set") String resourceSet) {
        this.goal = goal;
        this.cmd = cmd;
        this.env = env;
        this.configs = configs;
        this.cpus = cpus;
        this.memory = memory;
        this.ports = ports;
        this.healthCheck = healthCheck;
        this.readinessCheck = readinessCheck;
        this.volume = volume;
        this.volumes = volumes;
        this.resourceSet = resourceSet;
    }

    public Double getCpus() {
        return cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    public String getResourceSet() {
        return resourceSet;
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

    public String getCmd() {
        return cmd;
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
}
