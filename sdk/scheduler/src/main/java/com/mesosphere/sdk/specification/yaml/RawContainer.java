package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML container.
 */
public class RawContainer {
    private String imageName;

    public String getImageName() {
        return imageName;
    }

    @JsonProperty("image-name")
    public void setImageName(String imageName) {
        this.imageName = imageName;
    }
}
