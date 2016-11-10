package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos;

public interface ContainerSpec {
    // TODO: Remove protobuf from interface
    @JsonProperty("container")
    Protos.ContainerInfo getContainerInfo();
}
