package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecificationProvider;
import org.apache.mesos.state.StateStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a StateStore and a PlanSpecification the DefaultPlanFactory can generate a Plan.
 */
public class DefaultPlanFactory {
    private final DefaultPhaseFactory phaseFactory;

    public DefaultPlanFactory(
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            TaskSpecificationProvider taskSpecificationProvider) {
        this.phaseFactory = new DefaultPhaseFactory(new DefaultBlockFactory(
                stateStore, offerRequirementProvider, taskSpecificationProvider));
    }

    public Plan getPlan(ServiceSpecification serviceSpecification, List<String> errors)
            throws InvalidRequirementException {
        if (errors.isEmpty()) {
            return getPlan(serviceSpecification);
        }
        return DefaultPlan.withErrors(getPhases(serviceSpecification), errors);
    }

    public Plan getPlan(ServiceSpecification serviceSpecification) throws InvalidRequirementException {
        return DefaultPlan.fromList(getPhases(serviceSpecification));
    }

    private List<? extends Phase> getPhases(ServiceSpecification serviceSpecification)
            throws InvalidRequirementException {

        List<Phase> phases = new ArrayList<>();
        for (TaskSet taskSet : serviceSpecification.getTaskSets()) {
            phases.add(phaseFactory.getPhase(taskSet));
        }

        return phases;
    }
}
