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
    return getStrategy(getCurrentPhase()).getStatus();
  }
}
