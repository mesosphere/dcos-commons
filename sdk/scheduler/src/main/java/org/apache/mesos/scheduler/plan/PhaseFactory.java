package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.PodSet;

import java.util.List;

/**
 * A PhaseFactory transforms {@link PodSet}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(PodSet taskSet);
    Phase getPhase(PodSet taskSet, Strategy<Step> strategy);
    List<Phase> getPhases(List<PodSet> podSets, StrategyGenerator<Step> strategyGenerator);
}
