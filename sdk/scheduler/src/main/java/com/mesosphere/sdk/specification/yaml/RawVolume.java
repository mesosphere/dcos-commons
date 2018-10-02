package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML volume.
 */
public class RawVolume {

    private final String path;
    private final String type;
    private final int size;

    private RawVolume(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("size") int size) {
        this.path = path;
        this.type = type;
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public int getSize() {
        return size;
    }
}
