package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Raw YAML network. Null class for now, since only network name is supported, but will gain fields in the future.
 */
public class RawNetwork {
    private final List<Integer> hostPorts;
    private final List<Integer> containerPorts;
    private final Map<String, String> labels;

    @JsonCreator
    private RawNetwork(
            @JsonProperty("host-ports") List<Integer> hostPorts,
            @JsonProperty("container-ports") List<Integer> containerPorts,
            @JsonProperty("labels") Map<String, String> labels) {
        this.hostPorts = hostPorts;
        this.containerPorts = containerPorts;
        this.labels = labels;
    }

    private RawNetwork(String name) {
        this(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
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

    public Map<String, String> getLabels() {
        return labels;
    }

}

