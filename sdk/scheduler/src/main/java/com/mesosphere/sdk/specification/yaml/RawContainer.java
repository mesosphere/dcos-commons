package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML container.
 */
public class RawContainer {

    private final String imageName;

    private RawContainer(@JsonProperty("image-name") String imageName) {
        this.imageName = imageName;
    }

    public String getImageName() {
        return imageName;
    }
}
