package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Raw YAML task.
 */
public class RawTask {

    private final String goal;
    private final String cmd;
    private final String image;
    private final Map<String, String> env;
    private final WriteOnceLinkedHashMap<String, RawConfiguration> configurations;
    private final Collection<String> uris;
    private final Double cpus;
    private final Integer memory;
    private final WriteOnceLinkedHashMap<String, RawEndpoint> endpoints;
    private final RawHealthCheck healthCheck;
    private final RawVolume volume;
    private final WriteOnceLinkedHashMap<String, RawVolume> volumes;
    private final String resourceSet;

    private RawTask(
            @JsonProperty("goal") String goal,
            @JsonProperty("cmd") String cmd,
            @JsonProperty("image") String image,
            @JsonProperty("env") Map<String, String> env,
            @JsonProperty("configurations") WriteOnceLinkedHashMap<String, RawConfiguration> configurations,
            @JsonProperty("uris") Collection<String> uris,
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("endpoints") WriteOnceLinkedHashMap<String, RawEndpoint> endpoints,
            @JsonProperty("health-check") RawHealthCheck healthCheck,
            @JsonProperty("volume") RawVolume volume,
            @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes,
            @JsonProperty("resource-set") String resourceSet) {
        this.goal = goal;
        this.cmd = cmd;
        this.image = image;
        this.env = env;
        this.configurations = configurations;
        this.uris = uris;
        this.cpus = cpus;
        this.memory = memory;
        this.endpoints = endpoints;
        this.healthCheck = healthCheck;
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

    public WriteOnceLinkedHashMap<String, RawEndpoint> getEndpoints() {
        return endpoints;
    }

    public WriteOnceLinkedHashMap<String, RawConfiguration> getConfigurations() {
        return configurations;
    }

    public RawVolume getVolume() {
        return volume;
    }

    public WriteOnceLinkedHashMap<String, RawVolume> getVolumes() {
        return volumes;
    }
}
