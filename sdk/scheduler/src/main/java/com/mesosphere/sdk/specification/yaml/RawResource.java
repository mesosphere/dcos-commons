package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML resource.
 */
public class RawResource {

    private final String name;
    private final String value;
    private final String envKey;

    private RawResource(
            @JsonProperty("name") String name,
            @JsonProperty("value") String value,
            @JsonProperty("env-key") String envKey) {
        this.name = name;
        this.value = value;
        this.envKey = envKey;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getEnvKey() {
        return envKey;
    }
}
