package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A VolumeSpecification defines the features of a Volume.
 */
@JsonDeserialize(as = DefaultVolumeSpecification.class)
public interface VolumeSpecification extends ResourceSpecification {

    /**
     * Types of Volumes.
     */
    enum Type {
        ROOT,
        PATH,
        MOUNT
    }

    @JsonProperty("type")
    Type getType();

    @JsonProperty("container_path")
    String getContainerPath();
}
