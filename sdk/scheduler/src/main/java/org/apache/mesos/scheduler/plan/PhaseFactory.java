package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.specification.PodSpec;

/**
 * A PhaseFactory transforms {@link TaskSet}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(PodSpec podSpec);
    Phase getPhase(PodSpec podSpec, Strategy<Step> strategy);
}
