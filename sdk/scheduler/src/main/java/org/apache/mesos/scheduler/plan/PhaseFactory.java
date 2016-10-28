package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.TaskSet;

import java.util.List;

/**
 * A PhaseFactory transforms {@link TaskSet}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(TaskSet taskSet);
    Phase getPhase(TaskSet taskSet, Strategy<Step> strategy);
    List<Phase> getPhases(List<TaskSet> taskSets, StrategyGenerator<Step> strategyGenerator);
}
