package org.apache.mesos.specification;

import java.util.List;

/**
 * This class provides a default implementation of the PlanSpecification interface.
 */
public class DefaultPlanSpecification implements PlanSpecification {
    private List<PhaseSpecification> phaseSpecifications;

    public DefaultPlanSpecification(List<PhaseSpecification> phaseSpecifications) {
        this.phaseSpecifications = phaseSpecifications;
    }

    @Override
    public List<PhaseSpecification> getPhaseSpecifications() {
        return phaseSpecifications;
    }
}
