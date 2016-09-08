package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * A TaskSpecification is a simplified description of a Mesos Task.
 */
public interface TaskSpecification {
    String getName();
    Protos.CommandInfo getCommand();
    Collection<ResourceSpecification> getResources();
    Collection<VolumeSpecification> getVolumes();
}
