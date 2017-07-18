package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML vip.
 */
public class RawVip {

    private final int port;
    private final String prefix;

    /**
     * TODO(nickbp): {@code protocol} and {@code advertise} are no longer used. Remove entirely after September 2017.
     */
    private RawVip(
            @JsonProperty("port") int port,
            @JsonProperty("prefix") String prefix,
            @Deprecated @JsonProperty("protocol") String protocol,
            @Deprecated @JsonProperty("advertise") Boolean advertise) {
        this.port = port;
        this.prefix = prefix;
        if (protocol != null) {
            throw new IllegalArgumentException(
                    "VIP 'protocol' field is no longer supported. Remove the 'protocol' setting from your YAML.");
        }
        if (advertise != null) {
            throw new IllegalArgumentException(
                    "VIP 'advertise' field is no longer supported. Remove the 'advertise' setting from your YAML.");
        }
    }

    public int getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }
}
