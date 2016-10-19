package org.apache.mesos.scheduler.plan;

/**
 * Generates DefaultInstallStrategy objects when provided Phases.
 */
public class DefaultStrategyFactory implements PhaseStrategyFactory {


  @Override
  public PhaseStrategy getStrategy(Phase phase) {
    if (phase == null) {
      return null;
    } else if (phase instanceof ReconciliationPhase) {
      return ReconciliationStrategy.create(
              (ReconciliationPhase) phase);
    } else {
      return new DefaultInstallStrategy(phase);
    }
  }
}
