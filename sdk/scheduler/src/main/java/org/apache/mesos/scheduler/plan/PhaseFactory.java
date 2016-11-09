package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.TaskSet;

import java.util.List;

/**
 * A PhaseFactory transforms {@link TaskSet}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(List<PodSpec> podSpecs, Strategy<Step> strategy);
}
