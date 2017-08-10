package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML transport encryption.
 */
public class RawTransportEncryption {
    private final String name;
    private final String type;

    private RawTransportEncryption(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {

        return name;
    }

    public String getType() {
        return type;
    }
}
