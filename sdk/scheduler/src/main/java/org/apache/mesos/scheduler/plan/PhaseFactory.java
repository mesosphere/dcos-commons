package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.PodSetSpecification;

import java.util.List;

/**
 * A PhaseFactory transforms {@link PodSetSpecification}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(PodSetSpecification podSet);
    Phase getPhase(PodSetSpecification podSet, Strategy<Step> strategy);
    List<Phase> getPhases(List<PodSetSpecification> podSetSpecifications, StrategyGenerator<Step> strategyGenerator);
}
