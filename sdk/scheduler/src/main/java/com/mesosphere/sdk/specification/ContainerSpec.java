package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.ContainerVolume;

import java.util.Collection;
import java.util.Optional;

/**
 * Spec for defining a Container.
 */
@JsonDeserialize(as = DefaultContainerSpec.class)
public interface ContainerSpec {
    @JsonProperty("image-name")
    Optional<String> getImageName();

    @JsonProperty("rlimits")
    Collection<RLimit> getRLimits();

    @JsonProperty("volumes")
    Collection<ContainerVolume> getVolumes();
}
