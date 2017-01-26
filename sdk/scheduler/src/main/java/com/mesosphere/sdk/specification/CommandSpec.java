package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Specification for defining a command.
 */
@JsonDeserialize(as = DefaultCommandSpec.class)
public interface CommandSpec {
    @JsonProperty("value")
    String getValue();

    @JsonProperty("environment")
    Map<String, String> getEnvironment();

    @JsonProperty("user")
    Optional<String> getUser();

    @JsonProperty("uris")
    Collection<UriSpec> getUris();
}
