package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML resource.
 */
public class RawResource {
    private String name;
    private String value;
    private String envKey;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(String value) {
        this.value = value;
    }

    public String getEnvKey() {
        return envKey;
    }

    @JsonProperty("env-key")
    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }
}
