package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML resource.
 */
public class RawResource {

    private final String name;
    private final String value;
    private final String envKey;

    private RawResource(
            @JsonProperty("name") String name,
            @JsonProperty("value") String value,
            @JsonProperty("env-key") String envKey) {
        this.name = name;
        this.value = value;
        this.envKey = envKey;
    }

    private RawResource(Builder builder) {
        this(builder.name, builder.value, builder.envKey);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getEnvKey() {
        return envKey;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawResource} builder.
     */
    public static final class Builder {
        private String name;
        private String value;
        private String envKey;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder envKey(String envKey) {
            this.envKey = envKey;
            return this;
        }

        public RawResource build() {
            return new RawResource(this);
        }
    }
}
