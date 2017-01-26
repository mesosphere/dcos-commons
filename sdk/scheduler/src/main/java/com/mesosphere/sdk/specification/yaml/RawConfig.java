package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Configuration section.
 */
public class RawConfig {

    private final String template;
    private final String dest;

    private RawConfig(
            @JsonProperty("template") String template,
            @JsonProperty("dest") String dest) {
        this.template = template;
        this.dest = dest;
    }

    private RawConfig(Builder builder) {
        this(builder.template, builder.dest);
    }

    public String getTemplate() {
        return template;
    }

    public String getDest() {
        return dest;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawConfig} builder.
     */
    public static final class Builder {
        private String template;
        private String dest;

        private Builder() {
        }

        public Builder template(String template) {
            this.template = template;
            return this;
        }

        public Builder dest(String dest) {
            this.dest = dest;
            return this;
        }

        public RawConfig build() {
            return new RawConfig(this);
        }
    }
}
