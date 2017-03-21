package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Raw YAML network. Null class for now, since only network name is supported, but will gain fields in the future.
 */
public class RawNetwork {
    private final List<Integer> hostPorts;
    private final List<Integer> containerPorts;
    private final List<String> netgroups;

    @JsonCreator
    private RawNetwork(
            @JsonProperty("groups") List<String> netgroups,
            @JsonProperty("host-ports") List<Integer> hostPorts,
            @JsonProperty("container-ports") List<Integer> containerPorts) {
        this.hostPorts      = hostPorts;
        this.containerPorts = containerPorts;
        this.netgroups      = netgroups;
    }

    private RawNetwork(String name) {
        this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private RawNetwork(@JsonProperty("groups") List<String> netgroups) {
        this(netgroups, Collections.emptyList(), Collections.emptyList());
    }

    public List<Integer> getHostPorts() {
        return hostPorts;
    }

    public List<Integer> getContainerPorts() {
        return containerPorts;
    }

    public int numberOfPortMappings() throws IllegalArgumentException {
        if (hostPorts == null || containerPorts == null) {
            return 0;
        }
        if (hostPorts.size() != containerPorts.size()) {
            throw new IllegalStateException("You need to specify the same number of host ports and container ports");
        }

        return hostPorts.size();
    }

    public List<String> getNetgroups() {
        return netgroups;
    }

}

