package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Raw YAML container.
 */
public class RawContainer {
    private final String imageName;
    private final WriteOnceLinkedHashMap<String, RawRLimit> rlimits;
    private final Collection<ContainerVolume> volumes;

    private RawContainer(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("rlimits") WriteOnceLinkedHashMap<String, RawRLimit> rlimits,
            @JsonProperty("volumes") Collection<ContainerVolume> volumes) {
        this.imageName = imageName;
        this.rlimits = rlimits;
        this.volumes = volumes;
    }

    public LinkedHashMap<String, RawRLimit> getRLimits() {
        return rlimits;
    }

    public Collection<ContainerVolume> getVolumes() {
        return volumes;
    }

    public String getImageName() {
        return imageName;
    }
}
