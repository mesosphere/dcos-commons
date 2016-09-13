package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * A TaskTypeSpecification describes a Type of Task and how many should be launched.
 */
public interface TaskTypeSpecification {
    int getCount();
    String getTypeName();
    String getTaskName(int id);
    Protos.CommandInfo getCommand(int id);
    Collection<ResourceSpecification> getResources();
    Collection<VolumeSpecification> getVolumes();
}
