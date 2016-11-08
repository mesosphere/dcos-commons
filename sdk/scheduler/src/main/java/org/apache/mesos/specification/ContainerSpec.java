package org.apache.mesos.specification;

import org.apache.mesos.Protos;

/**
 * Created by gabriel on 11/7/16.
 */
public interface ContainerSpec {
    // TODO: Remove protobuf from interface
    Protos.ContainerInfo getContainerInfo();
}
