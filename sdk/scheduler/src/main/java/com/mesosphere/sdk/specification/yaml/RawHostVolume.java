package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RawHostVolume YAML spec.
 */
public class RawHostVolume {
    private final String hostPath;
    private final String containerPath;

    private RawHostVolume(
            @JsonProperty("host-path") String hostPath,
            @JsonProperty("container-path") String containerPath) {
        this.hostPath = hostPath;
        this.containerPath = containerPath;
    }

    public String getHostPath() {
        return hostPath;
    }

    public String getContainerPath() {
        return containerPath;
    }

}
