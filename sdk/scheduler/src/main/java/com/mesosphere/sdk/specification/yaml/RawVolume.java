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

    private RawVolume(Builder builder) {
        this(builder.path, builder.type, builder.size);
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

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawVolume} builder.
     */
    public static final class Builder {
        private String path;
        private String type;
        private int size;

        private Builder() {
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder protocol(int size) {
            this.size = size;
            return this;
        }

        public RawVolume build() {
            return new RawVolume(this);
        }
    }
}
