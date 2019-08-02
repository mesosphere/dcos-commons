package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * A HostVolumeSpec defines the features of a HostVolume.
 */
@JsonDeserialize(as = DefaultHostVolumeSpec.class)
public interface HostVolumeSpec {

  @JsonProperty("host-path")
  String getHostPath();

  @JsonProperty("container-path")
  String getContainerPath();

  @JsonProperty("mode")
  Optional<Protos.Volume.Mode> getMode();
}
