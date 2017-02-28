package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {

    private final Integer port;
    private final Integer min;
    private final Integer max;
    private final String envKey;
    private final RawVip vip;

    private RawPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("port-min") Integer min,
            @JsonProperty("port-max") Integer max,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("vip") RawVip vip) {
        this.port = port;
        this.min = min;
        this.max = max;
        this.envKey = envKey;
        this.vip = vip;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }

    public String getEnvKey() {
        return envKey;
    }

    public RawVip getVip() {
        return vip;
    }
}
