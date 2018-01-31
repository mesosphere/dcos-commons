package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * Represents a named group of resources.
 */
public interface ResourceSet {
    @JsonProperty("id")
    String getId();

    @JsonProperty("resource-specifications")
    Collection<ResourceSpec> getResources();

    @JsonProperty("volume-specifications")
    Collection<VolumeSpec> getVolumes();
}
