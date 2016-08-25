package org.apache.mesos.specification;

import java.util.List;

/**
 * Created by gabriel on 8/25/16.
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
