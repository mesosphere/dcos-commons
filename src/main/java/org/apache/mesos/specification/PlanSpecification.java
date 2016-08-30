package org.apache.mesos.specification;

import java.util.List;

/**
 * A Plan Specification provides an encapsulation of the PhaseSpecifications which should be executed.
 */
public interface PlanSpecification {
    List<PhaseSpecification> getPhaseSpecifications();
}
