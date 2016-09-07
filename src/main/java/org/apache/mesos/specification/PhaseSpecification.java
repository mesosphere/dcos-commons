package org.apache.mesos.specification;

import java.util.List;

/**
 * A Phase Specification provides an encapsulation of the TaskSpecifications which should be executed.
 */
public interface PhaseSpecification {
    String getName();
    List<TaskSpecification> getTaskSpecifications();
}
