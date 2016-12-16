package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Spec for defining a Container.
 */
@JsonDeserialize(as = DefaultContainerSpec.class)
public interface ContainerSpec {
    @JsonProperty("image-name")
    String getImageName();
}
