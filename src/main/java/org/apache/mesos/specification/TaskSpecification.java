package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;

/**
 * A TaskSpecification is a simplified description of a Mesos Task.
 */
public interface TaskSpecification {
    String getName();
    Protos.CommandInfo getCommand();
    Collection<ResourceSpecification> getResources();
    Optional<Collection<VolumeSpecification>> getVolumes();
}
