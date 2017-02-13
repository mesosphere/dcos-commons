package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.specification.util.RLimit;

import java.util.Collection;
import java.util.Optional;

/**
 * Spec for defining a Container.
 */
@JsonDeserialize(as = DefaultContainerSpec.class)
public interface ContainerSpec {
    @JsonProperty("image-name")
    Optional<String> getImageName();

    @JsonProperty("networks")
    Collection<NetworkSpec> getNetworks();

    @JsonProperty("rlimits")
    Collection<RLimit> getRLimits();
}
