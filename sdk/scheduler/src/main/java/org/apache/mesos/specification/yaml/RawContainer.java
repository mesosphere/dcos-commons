package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML container.
 */
public class RawContainer {
    private String imageName;

    public String getImageName() {
        return imageName;
    }

    @JsonProperty("imageName")
    public void setImageName(String imageName) {
        this.imageName = imageName;
    }
}
