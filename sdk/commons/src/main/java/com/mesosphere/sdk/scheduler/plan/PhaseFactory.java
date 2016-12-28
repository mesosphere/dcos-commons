package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.specification.PodSpec;

/**
 * A PhaseFactory transforms {@link PodSpec}s into {@link Phase}s.
 */
public interface PhaseFactory {
    Phase getPhase(PodSpec podSpec);
    Phase getPhase(PodSpec podSpec, Strategy<Step> strategy);
}
