package org.apache.mesos.specification;

import java.util.Collection;

/**
 * Created by gabriel on 11/7/16.
 */
public interface ResourceSet {
    String getId();
    Collection<ResourceSpecification> getResources();
    Collection<VolumeSpecification> getVolumes();
}
