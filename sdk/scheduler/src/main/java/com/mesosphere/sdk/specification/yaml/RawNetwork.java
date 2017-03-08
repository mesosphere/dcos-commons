package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Raw YAML network. Null class for now, since only network name is supported, but will gain fields in the future.
 */
public class RawNetwork {
    private final List<Integer> hostPorts;
    private final List<Integer> containerPorts;

    private RawNetwork(
            @JsonProperty("host-ports") List<Integer> hostPorts,
            @JsonProperty("container-ports") List<Integer> containerPorts) {
        this.hostPorts = hostPorts;
        this.containerPorts = containerPorts;
    }

    public List<Integer> getHostPorts() { return hostPorts; }

    public List<Integer> getContainerPorts() { return containerPorts; }
}