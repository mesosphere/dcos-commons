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

    public Integer getPort() {
        return port;
    }

    public String getEnvKey() {
        return envKey;
    }

    public RawVip getVip() {
        return vip;
    }
}
