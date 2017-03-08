package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RawCniPortMapping {
    private final String hostPorts;
    private final String containerPorts;

    private RawCniPortMapping(
            @JsonProperty("host-ports") String hostPorts,
            @JsonProperty("container-ports") String containerPorts) {
        this.hostPorts = hostPorts;
        this.containerPorts = containerPorts;
    }

    public String getHostPorts() { return hostPorts; }

    public String getContainerPorts() { return containerPorts; }

    @Override
    public String toString() {
        return "Host port: " + hostPorts + " Container port: " + containerPorts;
    }
}
