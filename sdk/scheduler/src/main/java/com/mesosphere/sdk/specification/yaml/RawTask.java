package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw YAML task.
 */
public class RawTask {

    private final String goal;
    private final String cmd;
    private final String image;
    private final Map<String, String> env;
    private final Collection<RawConfiguration> configurations;
    private final Collection<String> uris;
    private final Double cpus;
    private final Integer memory;
    private final Collection<RawPort> ports;
    private final LinkedHashMap<String, RawHealthCheck> healthChecks;
    private final Collection<RawVolume> volumes;
    private final String resourceSet;

    private RawTask(
            @JsonProperty("goal") String goal,
            @JsonProperty("cmd") String cmd,
            @JsonProperty("image") String image,
            @JsonProperty("env") Map<String, String> env,
            @JsonProperty("configurations") Collection<RawConfiguration> configurations,
            @JsonProperty("uris") Collection<String> uris,
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("ports") Collection<RawPort> ports,
            @JsonProperty("health-checks") LinkedHashMap<String, RawHealthCheck> healthChecks,
            @JsonProperty("volumes") Collection<RawVolume> volumes,
            @JsonProperty("resource-set") String resourceSet) {
        this.goal = goal;
        this.cmd = cmd;
        this.image = image;
        this.env = env;
        this.configurations = configurations;
        this.uris = uris;
        this.cpus = cpus;
        this.memory = memory;
        this.ports = ports;
        this.healthChecks = healthChecks;
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

    public LinkedHashMap<String, RawHealthCheck> getHealthChecks() {
        return healthChecks;
    }

    public String getGoal() {
        return goal;
    }

    public String getCmd() {
        return cmd;
    }

    public String getImage() {
        return image;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
    }

    public Collection<RawPort> getPorts() {
        return ports;
    }

    public Collection<RawConfiguration> getConfigurations() {
        return configurations;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }
}
