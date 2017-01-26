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
    private final Map<String, String> env;
    private final WriteOnceLinkedHashMap<String, RawConfig> configs;
    private final Collection<String> uris;
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
            @JsonProperty("uris") Collection<String> uris,
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
        this.uris = uris;
        this.cpus = cpus;
        this.memory = memory;
        this.ports = ports;
        this.healthCheck = healthCheck;
        this.readinessCheck = readinessCheck;
        this.volume = volume;
        this.volumes = volumes;
        this.resourceSet = resourceSet;
    }

    private RawTask(Builder builder) {
        this(
                builder.goal,
                builder.cmd,
                builder.env,
                builder.configs,
                builder.uris,
                builder.cpus,
                builder.memory,
                builder.ports,
                builder.healthCheck,
                builder.readinessCheck,
                builder.volume,
                builder.volumes,
                builder.resourceSet);
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

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
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

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawTask} builder.
     */
    public static final class Builder {
        private String goal;
        private String cmd;
        private Map<String, String> env;
        private WriteOnceLinkedHashMap<String, RawConfig> configs;
        private Collection<String> uris;
        private Double cpus;
        private Integer memory;
        private WriteOnceLinkedHashMap<String, RawPort> ports;
        private RawHealthCheck healthCheck;
        private RawReadinessCheck readinessCheck;
        private RawVolume volume;
        private WriteOnceLinkedHashMap<String, RawVolume> volumes;
        private String resourceSet;

        private Builder() {
        }

        public Builder goal(String goal) {
            this.goal = goal;
            return this;
        }

        public Builder cmd(String cmd) {
            this.cmd = cmd;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder configs(WriteOnceLinkedHashMap<String, RawConfig> configs) {
            this.configs = configs;
            return this;
        }

        public Builder uris(Collection<String> uris) {
            this.uris = uris;
            return this;
        }

        public Builder cpus(Double cpus) {
            this.cpus = cpus;
            return this;
        }

        public Builder memory(Integer memory) {
            this.memory = memory;
            return this;
        }

        public Builder ports(WriteOnceLinkedHashMap<String, RawPort> ports) {
            this.ports = ports;
            return this;
        }

        public Builder healthCheck(RawHealthCheck healthCheck) {
            this.healthCheck = healthCheck;
            return this;
        }

        public Builder readinessCheck(RawReadinessCheck readinessCheck) {
            this.readinessCheck = readinessCheck;
            return this;
        }

        public Builder volume(RawVolume volume) {
            this.volume = volume;
            return this;
        }

        public Builder volumes(WriteOnceLinkedHashMap<String, RawVolume> volumes) {
            this.volumes = volumes;
            return this;
        }

        public Builder resourceSet(String resourceSet) {
            this.resourceSet = resourceSet;
            return this;
        }

        public RawTask build() {
            return new RawTask(this);
        }
    }
}
