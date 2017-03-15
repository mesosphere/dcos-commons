package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML discovery info.
 */
public class RawDiscovery {
    private final String prefix;
    private final String visibility;

    private RawDiscovery(
            @JsonProperty("prefix") String prefix,
            @JsonProperty("visibility") String visibility) {
        this.prefix = prefix;
        this.visibility = visibility;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getVisibility() {
        return visibility;
    }
}
