package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw Scheduler configuration.
 */
public class RawScheduler {

    private final String principal;
    private final String zookeeper;

    private RawScheduler(
            @JsonProperty("principal") String principal,
            @JsonProperty("zookeeper") String zookeeper) {
        this.principal = principal;
        this.zookeeper = zookeeper;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getZookeeper() {
        return zookeeper;
    }
}
