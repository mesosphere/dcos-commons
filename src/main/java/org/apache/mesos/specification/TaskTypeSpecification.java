package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * A TaskTypeSpecification describes a Type of Task and how many should be launched.
 */
public interface TaskTypeSpecification {
    int getCount();
    String getName();
    Protos.CommandInfo getCommand();
    Collection<ResourceSpecification> getResources();
    Collection<VolumeSpecification> getVolumes();
}
