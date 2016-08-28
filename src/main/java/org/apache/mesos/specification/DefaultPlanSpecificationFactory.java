package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultPlanSpecificationFactory implements PlanSpecificationFactory {
    @Override
    public PlanSpecification getPlanSpecification(ServiceSpecification serviceSpecification) {
        List<PhaseSpecification> phaseSpecifications = new ArrayList<>();

        for (TaskTypeSpecification taskTypeSpecification : serviceSpecification.getTaskSpecifications()) {
            phaseSpecifications.add(
                    new DefaultPhaseSpecification(taskTypeSpecification.getName(), taskTypeSpecification));
        }

        return new DefaultPlanSpecification(phaseSpecifications);
    }
}
