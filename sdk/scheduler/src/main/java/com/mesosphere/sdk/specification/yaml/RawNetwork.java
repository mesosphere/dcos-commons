package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

/**
 * Raw YAML network. Null class for now, since only network name is supported, but will gain fields in the future.
 */
public class RawNetwork {
    private final String name;

    private RawNetwork(
            @JsonProperty("name") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
