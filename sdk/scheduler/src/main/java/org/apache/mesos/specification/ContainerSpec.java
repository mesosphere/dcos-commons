package org.apache.mesos.specification;

import org.apache.mesos.Protos;

public interface ContainerSpec {
    // TODO: Remove protobuf from interface
    Protos.ContainerInfo getContainerInfo();
}
