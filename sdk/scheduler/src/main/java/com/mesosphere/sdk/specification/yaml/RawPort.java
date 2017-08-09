package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {

    private final Integer port;
    private final String envKey;
    private final Boolean advertise;
    private final RawVip vip;

    private RawPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("advertise") Boolean advertise,
            @JsonProperty("vip") RawVip vip) {
        this.port = port;
        this.envKey = envKey;
        this.advertise = advertise;
        this.vip = vip;
    }

    public Integer getPort() {
        return port;
    }

    public String getEnvKey() {
        return envKey;
    }

    public boolean isAdvertised() {
        return advertise != null && advertise;
    }

    public RawVip getVip() {
        return vip;
    }
}
