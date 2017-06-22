package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw Scheduler configuration.
 */
public class RawScheduler {

    private final String principal;
    private final String user;
    private final Integer apiPort;
    private final String zookeeper;

    private RawScheduler(
            @JsonProperty("principal") String principal,
            @JsonProperty("user") String user,
            @JsonProperty("api-port") Integer apiPort,
            @JsonProperty("zookeeper") String zookeeper) {
        this.principal = principal;
        this.user = user;
        this.apiPort = apiPort;
        this.zookeeper = zookeeper;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getUser() {
        return user;
    }

    public Integer getApiPort() {
        return apiPort;
    }

    public String getZookeeper() {
        return zookeeper;
    }
}
