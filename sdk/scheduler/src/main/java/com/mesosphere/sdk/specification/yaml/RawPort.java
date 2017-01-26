package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {

    private final Integer port;
    private final String envKey;
    private final RawVip vip;

    private RawPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("vip") RawVip vip) {
        this.port = port;
        this.envKey = envKey;
        this.vip = vip;
    }

    private RawPort(Builder builder) {
        this(builder.port, builder.envKey, builder.vip);
    }

    public Integer getPort() {
        return port;
    }

    public String getEnvKey() {
        return envKey;
    }

    public RawVip getVip() {
        return vip;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawPort} builder.
     */
    public static final class Builder {
        private Integer port;
        private String envKey;
        private RawVip vip;

        private Builder() {
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder envKey(String envKey) {
            this.envKey = envKey;
            return this;
        }

        public Builder vip(RawVip vip) {
            this.vip = vip;
            return this;
        }

        public RawPort build() {
            return new RawPort(this);
        }
    }
}
