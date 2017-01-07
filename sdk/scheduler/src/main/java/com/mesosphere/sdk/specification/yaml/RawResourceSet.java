package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceSet.
 */
public class RawResourceSet {

    private final Double cpus;
    private final Integer memory;
    private final WriteOnceLinkedHashMap<String, RawEndpoint> endpoints;
    private final RawVolume volume;
    private final WriteOnceLinkedHashMap<String, RawVolume> volumes;

    private RawResourceSet(
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("endpoints") WriteOnceLinkedHashMap<String, RawEndpoint> endpoints,
            @JsonProperty("volume") RawVolume volume,
            @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes) {
        this.cpus = cpus;
        this.memory = memory;
        this.endpoints = endpoints;
        this.volume = volume;
        this.volumes = volumes;
    }

    public Double getCpus() {
        return cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    public WriteOnceLinkedHashMap<String, RawEndpoint> getEndpoints() {
        return endpoints;
    }

    public RawVolume getVolume() {
        return volume;
    }

    public WriteOnceLinkedHashMap<String, RawVolume> getVolumes() {
        return volumes;
    }
}
