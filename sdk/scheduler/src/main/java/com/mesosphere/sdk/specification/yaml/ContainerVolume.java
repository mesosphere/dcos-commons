package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Raw YAML container volume.
 */
public class ContainerVolume {
    private final String mode;
    private final String containerPath;
    private final String hostPath;

    private ContainerVolume(
            @JsonProperty("mode") String mode,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("host-path") String hostPath) {
        this.mode = mode;
        this.containerPath = containerPath;
        this.hostPath = hostPath;
    }

    @JsonProperty("mode")
    public String getMode() {
        return mode;
    }

    @JsonProperty("container-path")
    public String getContainerPath() {
        return containerPath;
    }

    @JsonProperty("host-path")
    public String getHostPath() {
        return hostPath;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
