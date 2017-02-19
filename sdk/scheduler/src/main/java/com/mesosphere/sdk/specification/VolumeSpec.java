package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A VolumeSpec defines the features of a Volume.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface VolumeSpec extends ResourceSpec {

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

    @JsonProperty("container-path")
    String getContainerPath();

    /* ignore container-path */
    static boolean compare(VolumeSpec a, VolumeSpec b){
        if (a.getType().equals(b.getType())) {
            return ResourceSpec.compare(a, b);
        }
        return false;
    }
}
