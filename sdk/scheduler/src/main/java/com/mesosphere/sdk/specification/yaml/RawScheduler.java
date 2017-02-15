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
    private final String user;

    private RawScheduler(
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("api-port") Integer apiPort,
            @JsonProperty("zookeeper") String zookeeper,
            @JsonProperty("user") String user) {
        this.role = role;
        this.principal = principal;
        this.apiPort = apiPort;
        this.zookeeper = zookeeper;
        this.user = user;
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

    public String getUser() {
        return user;
    }
}
