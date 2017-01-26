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

    private RawContainer(Builder builder) {
        this(builder.imageName, builder.rlimits);
    }

    public LinkedHashMap<String, RawRLimit> getRLimits() {
        return rlimits;
    }

    public String getImageName() {
        return imageName;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawContainer} builder.
     */
    public static final class Builder {
        private String imageName;
        private WriteOnceLinkedHashMap<String, RawRLimit> rlimits;

        private Builder() {
        }

        public Builder imageName(String imageName) {
            this.imageName = imageName;
            return this;
        }

        public Builder dest(WriteOnceLinkedHashMap<String, RawRLimit> rlimits) {
            this.rlimits = rlimits;
            return this;
        }

        public RawContainer build() {
            return new RawContainer(this);
        }
    }
}
