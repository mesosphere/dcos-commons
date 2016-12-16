package com.mesosphere.sdk.specification.yaml;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceSet.
 */
public class RawResourceSet {

    private final Double cpus;
    private final Integer memory;
    private final Collection<RawPort> ports;
    private final Collection<RawVolume> volumes;

    private RawResourceSet(
            @JsonProperty("cpus") Double cpus,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("ports") Collection<RawPort> ports,
            @JsonProperty("volumes") Collection<RawVolume> volumes) {
        this.cpus = cpus;
        this.memory = memory;
        this.ports = ports;
        this.volumes = volumes;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    public Double getCpus() {
        return cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    public Collection<RawPort> getPorts() {
        return ports;
    }
}
