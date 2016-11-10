package org.apache.mesos.specification;

import java.util.Collection;

/**
 * Represents a named group of resources.
 */
public interface ResourceSet {
    String getId();

    Collection<ResourceSpecification> getResources();

    Collection<VolumeSpecification> getVolumes();
}
