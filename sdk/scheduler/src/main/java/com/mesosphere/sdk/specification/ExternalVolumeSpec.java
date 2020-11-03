package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Specification object for external volumes.
 */
@JsonDeserialize(as = PortworxVolumeSpec.class)
public interface ExternalVolumeSpec {

  // The type of external-volume. Currently only DOCKER is supported.
  @JsonProperty("type")
  Type getType();

  // Path where the external volume is surfaced in the launched container.
  @JsonProperty("container-path")
  String getContainerPath();

  /**
   * Types of external volumes.
   */
  enum Type {
    DOCKER
  }
}
