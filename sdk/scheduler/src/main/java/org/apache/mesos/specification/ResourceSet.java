package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;

/**
 * Represents a named group of resources.
 */
@JsonDeserialize(as = DefaultResourceSet.class)
public interface ResourceSet {
    @JsonProperty("id")
    String getId();

    @JsonProperty("resource_specifications")
    Collection<ResourceSpecification> getResources();

    @JsonProperty("volume_specifications")
    Collection<VolumeSpecification> getVolumes();
}
