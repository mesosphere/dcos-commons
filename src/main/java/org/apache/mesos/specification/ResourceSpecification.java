package org.apache.mesos.specification;

import org.apache.mesos.Protos;

/**
 * A ResourceSpecification encapsulates a Mesos Resource that may be used by a Task and therefore specified in a
 * TaskSpecification.
 */
public interface ResourceSpecification {
    Protos.Value getValue();
    String getName();
    String getRole();
    String getPrincipal();
}
