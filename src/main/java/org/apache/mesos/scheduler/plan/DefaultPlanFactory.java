package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.PhaseSpecification;
import org.apache.mesos.specification.PlanSpecification;
import org.apache.mesos.state.StateStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a StateStore and a PlanSpecification the DefaultPlanFactory can generate a Plan.
 */
public class DefaultPlanFactory {
    private final DefaultPhaseFactory phaseFactory;

    public DefaultPlanFactory(StateStore stateStore) {
        this.phaseFactory = new DefaultPhaseFactory(new DefaultBlockFactory(stateStore));
    }

    public Plan getPlan(PlanSpecification planSpecification) throws InvalidRequirementException {
        return DefaultPlan.fromList(getPhases(planSpecification));
    }

    private List<? extends Phase> getPhases(PlanSpecification planSpecification) throws InvalidRequirementException {
        List<Phase> phases = new ArrayList<>();
        for (PhaseSpecification phaseSpecification : planSpecification.getPhaseSpecifications()) {
            phases.add(phaseFactory.getPhase(phaseSpecification));
        }

        return phases;
    }
}
