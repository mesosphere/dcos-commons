package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface ExternalVolumeSpec {

    // The type of external-volume. Currently only DOCKER is supported.
    @JsonProperty("type")
    Type getType();

    // The provider of the external-volume. Currently only PWX is supported.
    @JsonProperty("provider")
    Provider getProvider();

    // Size of the external volume. The capability to grow or shrink this volume is delegated to the provider.
    @JsonProperty("size")
    int getSize();

    // Path where the external volume is surfaced in the launched container.
    @JsonProperty("container-path")
    String getContainerPath();

    enum Type {
        DOCKER
    }

    enum Provider {
        PWX
    }
}
