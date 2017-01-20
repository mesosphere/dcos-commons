package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceSet.
 */
public class RawResourceSet {

    private final Double cpus;
    private final Integer memory;
    private final WriteOnceLinkedHashMap<String, RawPort> ports;
    private final RawVolume volume;
    private final WriteOnceLinkedHashMap<String, RawVolume> volumes;

    private RawResourceSet(
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("ports") WriteOnceLinkedHashMap<String, RawPort> ports,
            @JsonProperty("volume") RawVolume volume,
            @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes) {
        this.cpus = cpus;
        this.memory = memory;
        this.ports = ports;
        this.volume = volume;
        this.volumes = volumes;
    }

    private RawResourceSet(Builder builder) {
        this(
                builder.cpus,
                builder.memory,
                builder.ports,
                builder.volume,
                builder.volumes);
    }

    public Double getCpus() {
        return cpus;
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

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawResourceSet} builder.
     */
    public static final class Builder {
        private Double cpus;
        private Integer memory;
        private WriteOnceLinkedHashMap<String, RawPort> ports;
        private RawVolume volume;
        private WriteOnceLinkedHashMap<String, RawVolume> volumes;

        private Builder() {
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

        public Builder volume(RawVolume volume) {
            this.volume = volume;
            return this;
        }

        public Builder volumes(WriteOnceLinkedHashMap<String, RawVolume> volumes) {
            this.volumes = volumes;
            return this;
        }

        public RawResourceSet build() {
            return new RawResourceSet(this);
        }
    }
}
