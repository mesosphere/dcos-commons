package com.mesosphere.sdk.specification.yaml;

import java.util.Collection;

/**
 * Raw YAML ResourceSet.
 */
public class RawResourceSet {
    String id;
    Double cpus;
    Integer memory;
    Collection<RawPort> ports;
    Collection<RawVolume> volumes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    public void setVolumes(Collection<RawVolume> volumes) {
        this.volumes = volumes;
    }

    public Double getCpus() {
        return cpus;
    }

    public void setCpus(Double cpus) {
        this.cpus = cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public Collection<RawPort> getPorts() {
        return ports;
    }

    public void setPorts(Collection<RawPort> ports) {
        this.ports = ports;
    }
}
