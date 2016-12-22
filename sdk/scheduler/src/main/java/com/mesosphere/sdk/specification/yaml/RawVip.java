package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML vip.
 */
public class RawVip {

    private final int port;
    private final String prefix;

    private RawVip(
            @JsonProperty("port") int port,
            @JsonProperty("prefix") String prefix) {
        this.port = port;
        this.prefix = prefix;
    }

    public int getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }
}
