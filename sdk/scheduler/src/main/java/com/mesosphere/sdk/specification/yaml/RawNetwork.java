package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

/**
 * Raw YAML network. Null class for now, since only network name is supported, but will gain fields in the future.
 */
public class RawNetwork {
    private final String name;
    private final RawCniPortMapping rawCniPortMapping;

    private RawNetwork(
            @JsonProperty("name") String name,
            @JsonProperty("port-mappings") RawCniPortMapping rawCniPortMapping) {
        this.name              = name;
        this.rawCniPortMapping = rawCniPortMapping;
    }

    public String getName() { return name; }

    public RawCniPortMapping getRawCniPortMapping() { return rawCniPortMapping; }
}