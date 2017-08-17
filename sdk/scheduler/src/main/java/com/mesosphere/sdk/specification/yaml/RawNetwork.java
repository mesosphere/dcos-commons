package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Raw YAML network.
 */
public class RawNetwork {
    private final List<Integer> hostPorts;
    private final List<Integer> containerPorts;
    private final String labelsCsv;

    @JsonCreator
    private RawNetwork(
            @JsonProperty("host-ports") List<Integer> hostPorts,
            @JsonProperty("container-ports") List<Integer> containerPorts,
            @JsonProperty("labels") String labels) {
        this.hostPorts = hostPorts;
        this.containerPorts = containerPorts;
        this.labelsCsv = labels;
    }

    private RawNetwork(String name) {
        this(Collections.emptyList(), Collections.emptyList(), "");
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

    public String getLabelsCsv() {
        return labelsCsv;
    }

    public List<String[]> getValidatedLabels() throws IllegalArgumentException {
        List<String[]> kvs = Arrays.stream(labelsCsv.split(","))
                .map(s -> s.split(":"))
                .collect(Collectors.toList());
        kvs.forEach(kv -> {
            if (kv.length != 2) {
                throw new IllegalArgumentException(String.format("Illegal label string, got %s, should be " +
                        "comma-seperated key value pairs (seperated by colons)." +
                        " For example: k_0:v_0,k_1:v_1,...,k_n:v_n", labelsCsv));
            }
        });
        return kvs;
    }
}

