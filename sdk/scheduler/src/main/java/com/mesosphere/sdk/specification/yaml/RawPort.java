package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {

    private final String name;
    private final Integer port;
    private final RawVip vip;

    private RawPort(
            @JsonProperty("name") String name,
            @JsonProperty("port") Integer port,
            @JsonProperty("vip") RawVip vip) {
        this.name = name;
        this.port = port;
        this.vip = vip;
    }

    public String getName() {
        return name;
    }

    public Integer getPort() {
        return port;
    }

    public RawVip getVip() {
        return vip;
    }
}
