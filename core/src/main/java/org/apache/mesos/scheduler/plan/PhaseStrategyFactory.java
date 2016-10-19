package org.apache.mesos.scheduler.plan;

/**
 * Used to create different Phase Strategies, such as Install or Update.
 */
public interface PhaseStrategyFactory {

  PhaseStrategy getStrategy(Phase phase);

}
