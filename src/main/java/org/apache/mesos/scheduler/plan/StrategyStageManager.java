package org.apache.mesos.scheduler.plan;

/**
 * Implements support for Stage Strategy behavior on top of the {@link DefaultStageManager}.
 */
public class StrategyStageManager extends DefaultStageManager {

  public StrategyStageManager(Stage stage, PhaseStrategyFactory strategyFactory) {
    super(stage, strategyFactory);
  }

  /**
   * Adds support for passing up {@link Status.Waiting} when any Phase is Waiting.
   */
  @Override
  public Status getStatus() {
    // Staging strategy customization: Return Waiting if any underlying phase is Waiting.
    for (Phase phase : stage.getPhases()) {
      PhaseStrategy phaseStrategy = getStrategy(phase);
      if (phaseStrategy.getStatus() == Status.Waiting) {
        return Status.Waiting;
      }
    }
    // Fall back to default behavior in all other cases.
    return super.getStatus();
  }

  /**
   * Returns whether the provided {@link Block} is flagged by its {@link PhaseStrategy} as having
   * a decision point.
   */
  @Override
  public boolean hasDecisionPoint(Block block) {
    synchronized (phaseStrategies) {
      for (PhaseStrategy phaseStrategy : phaseStrategies.values()) {
        Phase phase = phaseStrategy.getPhase();
        for (Block blk : phase.getBlocks()) {
          if (blk.getId().equals(block.getId())) {
            return phaseStrategy.hasDecisionPoint(block);
          }
        }
      }
    }

    return false;
  }
}
