package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw Scheduler configuration.
 */
public class RawScheduler {

    private final String role;
    private final String principal;
    private final Integer apiPort;
    private final String zookeeper;

    private RawScheduler(
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("api-port") Integer apiPort,
            @JsonProperty("zookeeper") String zookeeper) {
        this.role = role;
        this.principal = principal;
        this.apiPort = apiPort;
        this.zookeeper = zookeeper;
    }

    private RawScheduler(Builder builder) {
        this(builder.role, builder.principal, builder.apiPort, builder.zookeeper);
    }

    public String getRole() {
        return role;
    }

    public String getPrincipal() {
        return principal;
    }

    public Integer getApiPort() {
        return apiPort;
    }

    public String getZookeeper() {
        return zookeeper;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawScheduler} builder.
     */
    public static final class Builder {
        private String role;
        private String principal;
        private Integer apiPort;
        private String zookeeper;

        private Builder() {
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder apiPort(Integer apiPort) {
            this.apiPort = apiPort;
            return this;
        }

        public Builder zookeeper(String zookeeper) {
            this.zookeeper = zookeeper;
            return this;
        }

        public RawScheduler build() {
            return new RawScheduler(this);
        }
    }
}
