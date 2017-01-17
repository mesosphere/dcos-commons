package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML vip.
 */
public class RawVip {

    private final int port;
    private final String prefix;
    private final String protocol;
    private final Boolean advertise;

    private RawVip(
            @JsonProperty("port") int port,
            @JsonProperty("prefix") String prefix,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("advertise") Boolean advertise) {
        this.port = port;
        this.prefix = prefix;
        this.protocol = protocol;
        this.advertise = advertise;
    }

    public int getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getProtocol() {
        return protocol;
    }

    public Boolean isAdvertised() {
        return advertise;
    }
}
