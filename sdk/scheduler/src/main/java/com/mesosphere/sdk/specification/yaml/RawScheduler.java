package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw Scheduler configuration.
 */
public class RawScheduler {

    private final String principal;
    private final String principalSecret;
    private final String zookeeper;
    private final String user;

    private RawScheduler(
            @JsonProperty("principal") String principal,
            @JsonProperty("principal-secret") String principalSecret,
            @JsonProperty("zookeeper") String zookeeper,
            @JsonProperty("user") String user) {
        this.principal = principal;
        this.principalSecret = principalSecret;
        this.zookeeper = zookeeper;
        this.user = user;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getPrincipalSecret() { return principalSecret; }

    public String getZookeeper() {
        return zookeeper;
    }

    public String getUser() {
        return user;
    }
}
