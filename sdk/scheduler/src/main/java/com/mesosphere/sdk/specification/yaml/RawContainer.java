package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Raw YAML container.
 */
public class RawContainer {
    private final String imageName;
    private final WriteOnceLinkedHashMap<String, RawRLimit> rlimits;

    private RawContainer(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("rlimits") WriteOnceLinkedHashMap<String, RawRLimit> rlimits) {
        this.imageName = imageName;

        if (rlimits != null) {
            this.rlimits = rlimits;
        } else {
            this.rlimits = new WriteOnceLinkedHashMap<>();
        }
    }

    public LinkedHashMap<String, RawRLimit> getRLimits() {
        return rlimits;
    }

    public String getImageName() {
        return imageName;
    }
}
