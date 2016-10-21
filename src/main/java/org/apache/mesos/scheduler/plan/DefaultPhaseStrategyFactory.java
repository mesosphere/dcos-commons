package org.apache.mesos.scheduler.plan;

/**
 * Generates DefaultPhaseStrategy objects when provided Phases.
 */
public class DefaultPhaseStrategyFactory implements PhaseStrategyFactory {

  @Override
  public PhaseStrategy getStrategy(Phase phase) {
    if (phase == null) {
      return null;
    } else if (phase instanceof ReconciliationPhase) {
      return ReconciliationStrategy.create(
          (ReconciliationPhase) phase);
    } else {
      return new DefaultPhaseStrategy(phase);
    }
  }
}
