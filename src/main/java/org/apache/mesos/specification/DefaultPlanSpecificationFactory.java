package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a default implementation of the PlanSpecifiactionFactory interface.
 */
public class DefaultPlanSpecificationFactory implements PlanSpecificationFactory {
    @Override
    public PlanSpecification getPlanSpecification(ServiceSpecification serviceSpecification) {
        List<PhaseSpecification> phaseSpecifications = new ArrayList<>();

        for (TaskTypeSpecification taskTypeSpecification : serviceSpecification.getTaskSpecifications()) {
            phaseSpecifications.add(new DefaultPhaseSpecification(taskTypeSpecification));
        }

        return new DefaultPlanSpecification(phaseSpecifications);
    }
}
