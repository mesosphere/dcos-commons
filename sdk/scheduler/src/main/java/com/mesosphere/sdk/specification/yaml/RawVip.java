package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML vip.
 */
public class RawVip {

    private final int port;
    private final String prefix;
    private final String protocol;
    private final Boolean advertise;

    private RawVip(
            @JsonProperty("port") int port,
            @JsonProperty("prefix") String prefix,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("advertise") Boolean advertise) {
        this.port = port;
        this.prefix = prefix;
        this.protocol = protocol;
        this.advertise = advertise;
    }

    private RawVip(Builder builder) {
        this(builder.port, builder.prefix, builder.protocol, builder.advertise);
    }

    public int getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getProtocol() {
        return protocol;
    }

    public Boolean isAdvertised() {
        return advertise;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawVip} builder.
     */
    public static final class Builder {
        private int port;
        private String prefix;
        private String protocol;
        private Boolean advertise;

        private Builder() {
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder advertise(Boolean advertise) {
            this.advertise = advertise;
            return this;
        }

        public RawVip build() {
            return new RawVip(this);
        }
    }
}
