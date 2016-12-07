package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML volume.
 */
public class RawVolume {
    String path;
    String type;
    int size;

    public String getPath() {
        return path;
    }

    @JsonProperty("path")
    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    @JsonProperty("size")
    public void setSize(int size) {
        this.size = size;
    }
}
