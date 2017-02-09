package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Raw YAML container.
 */
public class RawContainer {
    private final String imageName;
    private final WriteOnceLinkedHashMap<String, RawNetwork> networks;
    private final WriteOnceLinkedHashMap<String, RawRLimit> rlimits;

    private RawContainer(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("networks") WriteOnceLinkedHashMap<String, RawNetwork> networks,
            @JsonProperty("rlimits") WriteOnceLinkedHashMap<String, RawRLimit> rlimits) {
        this.imageName = imageName;
        this.networks = networks == null ? new WriteOnceLinkedHashMap<>() : networks;
        this.rlimits = rlimits == null ? new WriteOnceLinkedHashMap<>() : rlimits;
    }

    public LinkedHashMap<String, RawNetwork> getNetworks() {
        return networks;
    }

    public LinkedHashMap<String, RawRLimit> getRLimits() {
        return rlimits;
    }

    public String getImageName() {
        return imageName;
    }
}
