package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A HostVolumeSpec defines the features of a HostVolume.
 */
@JsonDeserialize(as = DefaultHostVolumeSpec.class)
public interface HostVolumeSpec {

    @JsonProperty("host-path")
    String getHostPath();

    @JsonProperty("container-path")
    String getContainerPath();

}
