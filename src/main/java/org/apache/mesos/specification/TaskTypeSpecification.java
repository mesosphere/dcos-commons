package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * A TaskTypeSpecification describes the count of a particular TaskSpecification which should be launched.
 */
public interface TaskTypeSpecification {
    int getCount();
    String getName();
    Protos.CommandInfo getCommand(int id);
    Collection<ResourceSpecification> getResources();
    Collection<VolumeSpecification> getVolumes();
}
