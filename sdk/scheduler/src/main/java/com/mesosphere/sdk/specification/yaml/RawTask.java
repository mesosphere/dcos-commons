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
    private String name;
    private String goal;
    private String cmd;
    private String image;
    private Map<String, String> env;
    private Collection<RawConfiguration> configurations;
    private Collection<String> uris;
    private Double cpus;
    private Integer memory;
    private Collection<RawPort> ports;
    private LinkedHashMap<String, RawHealthCheck> healthChecks;
    private Collection<RawVolume> volumes;
    private String resourceSet;

    public Double getCpus() {
        return cpus;
    }

    @JsonProperty("cpus")
    public void setCpus(Double cpus) {
        this.cpus = cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    @JsonProperty("memory")
    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getResourceSet() {
        return resourceSet;
    }

    @JsonProperty("resource-set")
    public void setResourceSet(String resourceSet) {
        this.resourceSet = resourceSet;
    }

    public LinkedHashMap<String, RawHealthCheck> getHealthChecks() {
        return healthChecks;
    }

    @JsonProperty("health-checks")
    public void setHealthChecks(LinkedHashMap<String, RawHealthCheck> healthChecks) {
        this.healthChecks = healthChecks;
    }

    public String getGoal() {
        return goal;
    }

    @JsonProperty("goal")
    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getCmd() {
        return cmd;
    }

    @JsonProperty("cmd")
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getImage() {
        return image;
    }

    @JsonProperty("image")
    public void setImage(String image) {
        this.image = image;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    @JsonProperty("env")
    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
    }

    @JsonProperty("uris")
    public void setUris(Collection<String> uris) {
        this.uris = uris;
    }

    public Collection<RawPort> getPorts() {
        return ports;
    }

    @JsonProperty("ports")
    public void setPorts(Collection<RawPort> ports) {
        this.ports = ports;
    }

    public Collection<RawConfiguration> getConfigurations() {
        return configurations;
    }

    @JsonProperty("configurations")
    public void setConfigurations(Collection<RawConfiguration> configurations) {
        this.configurations = configurations;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    @JsonProperty("volumes")
    public void setVolumes(Collection<RawVolume> volumes) {
        this.volumes = volumes;
    }
}
