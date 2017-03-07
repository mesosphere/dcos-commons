package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {

    private final Integer port;
    private final Integer begin;
    private final Integer end;
    private final String envKey;
    private final RawVip vip;

    private RawPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("port-begin") Integer begin,
            @JsonProperty("port-end") Integer end,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("vip") RawVip vip) {
        this.port = port;
        this.begin = begin;
        this.end = end;
        this.envKey = envKey;
        this.vip = vip;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getBegin() {
        return begin;
    }

    public Integer getEnd() {
        return end;
    }

    public String getEnvKey() {
        return envKey;
    }

    public RawVip getVip() {
        return vip;
    }
}
