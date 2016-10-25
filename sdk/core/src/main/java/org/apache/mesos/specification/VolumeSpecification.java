package org.apache.mesos.specification;

/**
 * A VolumeSpecification defines the features of a Volume.
 */
public interface VolumeSpecification extends ResourceSpecification {

    /**
     * Types of Volumes.
     */
    enum Type {
        ROOT,
        PATH,
        MOUNT
    }

    Type getType();
    String getContainerPath();
}
