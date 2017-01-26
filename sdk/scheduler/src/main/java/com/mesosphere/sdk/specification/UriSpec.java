package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;

/**
 * Specification for defining a URI.
 */
@JsonDeserialize(as = DefaultUriSpec.class)
public interface UriSpec {
    @JsonProperty("value")
    String getValue();

    @JsonProperty("executable")
    boolean isExecutable();

    @JsonProperty("extract")
    boolean shouldExtract();

    @JsonProperty("cache")
    boolean shouldCache();

    @JsonProperty("output-file")
    Optional<String> getOutputFile();
}
