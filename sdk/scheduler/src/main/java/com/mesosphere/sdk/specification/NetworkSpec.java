package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Spec for defining a container's network membership.
 */
@JsonDeserialize(as = DefaultNetworkSpec.class)
public interface NetworkSpec {
    @JsonProperty("name")
    String getName();
}
