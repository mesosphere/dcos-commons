package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Provides the default implementation for plan manager.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultStageManager implements StageManager {
  protected static final Logger LOGGER =
    LoggerFactory.getLogger(DefaultStageManager.class);

  /**
   * Access to {@code phaseStrategies} MUST be locked/synchronized against
   * {@code phaseStrategies}.
   */
  protected final Map<UUID, PhaseStrategy> phaseStrategies = new HashMap<>();
  protected volatile Stage stage;

  private final PhaseStrategyFactory strategyFactory;

  public DefaultStageManager(final Stage stage,
    final PhaseStrategyFactory
      strategyFactory) {
    this.strategyFactory = strategyFactory;
    setStage(stage);
  }

  @Override
  public void setStage(final Stage stage) {
    LOGGER.info("Setting stage : state = {}", stage);
    this.stage = stage;
  }

  @Override
  public Stage getStage() {
    return stage;
  }

  /**
   * Returns the first {@link Phase} in the {@link Stage} which isn't marked complete.
   */
  @Override
  public Phase getCurrentPhase() {
    for (Phase phase : stage.getPhases()) {
      if (!phase.isComplete()) {
        LOGGER.debug("Phase {} is NOT complete.", phase.getId());
        return phase;
      } else {
        LOGGER.debug("Phase {} is complete.", phase.getId());
      }
    }
    LOGGER.debug("All phases are complete.");
    return null;
  }

  public Phase getPhase(final UUID phaseId) {
    if (phaseId == null) {
      LOGGER.warn("null phaseId");
      return null;
    }
    for (Phase phase : stage.getPhases()) {
      if (phaseId.equals(phase.getId())) {
        return phase;
      }
    }
    return null;
  }

  @Override
  public Block getCurrentBlock() {
    PhaseStrategy currPhase = getCurrentPhaseStrategy();
    return (currPhase != null) ? currPhase.getCurrentBlock() : null;
  }

  @Override
  public boolean isComplete() {
    return stage.getErrors().isEmpty() ? getCurrentPhase() == null : false;
  }

  @Override
  public void proceed() {
    LOGGER.info("Proceeding with staged execution");
    final PhaseStrategy currPhase = getCurrentPhaseStrategy();
    if (currPhase != null) {
      currPhase.proceed();
      LOGGER.info("Proceeding with current phase: phase = {}",
        currPhase);
    } else {
      LOGGER.info("No phase to proceed");
    }
  }

  @Override
  public void interrupt() {
    LOGGER.info("Interrupting staged execution");
    final PhaseStrategy currPhase = getCurrentPhaseStrategy();
    if (currPhase != null) {
      currPhase.interrupt();
      LOGGER.info("Interrupted current phase: phase = {}", currPhase);
    } else {
      LOGGER.info("No phase to interrupt");
    }
  }

  @Override
  public boolean isInterrupted() {
    return getStatus() == Status.Waiting;
  }

  @Override
  public void restart(final UUID phaseId, final UUID blockId) {
    LOGGER.info(
      "Restarting phase : phaseId = {}, blockId = {}",
      phaseId, blockId);
    PhaseStrategy strategy = getPhaseStrategy(phaseId, blockId);
    if (strategy == null) {
      return;
    }
    strategy.restart(blockId);
    LOGGER.info("Restarted phase : phaseId = {}, blockId = {}", phaseId,
      blockId);
  }

  @Override
  public void forceComplete(final UUID phaseId, final UUID blockId) {
    LOGGER.info(
      "Forcing completion : phaseId = {}, blockId = {}",
      phaseId, blockId);
    PhaseStrategy strategy = getPhaseStrategy(phaseId, blockId);
    if (strategy == null) {
      return;
    }
    strategy.forceComplete(blockId);
    LOGGER.info("Forced completion : phaseId = {}, blockId = {}", phaseId,
      blockId);
  }

  @Override
  public void update(final Protos.TaskStatus status) {
    LOGGER.info("Received status update : status = {}", status);

    final PhaseStrategy currentPhaseStrategy = getCurrentPhaseStrategy();
    if (currentPhaseStrategy != null) {
      final Phase currentPhase = currentPhaseStrategy.getPhase();
      if (currentPhase != null) {
        final List<? extends Block> blocks = currentPhase.getBlocks();
        for (Block block : blocks) {
          LOGGER.info("Updated current block with status: block = {}", block);
          block.update(status);
        }
      } else {
        LOGGER.info("currentPhase is null. No blocks to receive status");
      }
    } else {
      LOGGER.info("currentPhaseStrategy is null. No blocks to receive status");
    }
  }

  @Override
  public boolean hasDecisionPoint(final Block block) {
    return false;
  }

  @Override
  public Status getStatus() {
    if (!stage.getErrors().isEmpty()) {
      return Status.Error;
    }

    if (stage.getPhases().isEmpty()) {
      LOGGER.warn("Stage doesn't have any phases");
      return Status.Complete;
    }

    if (anyHaveStatus(Status.Waiting, stage)) {
      LOGGER.info("Atleast one phase have status: " + Status.Waiting);
      return Status.Waiting;
    } else if (allHaveStatus(Status.Complete, stage)) {
      LOGGER.info("All phases have status: " + Status.Complete);
      return Status.Complete;
    } else if (allHaveStatus(Status.Pending, stage)) {
      LOGGER.info("All phases have status: " + Status.Pending);
      return Status.Pending;
    } else if (anyHaveStatus(Status.InProgress, stage)) {
      LOGGER.info("Atleast one phase have status: " + Status.InProgress);
      return Status.InProgress;
    } else {
      LOGGER.error("Unexpected state. Stage: " + stage);
      return null;
    }
  }

  public boolean allHaveStatus(Status status, Stage stage) {
    final List<? extends Phase> phases = stage.getPhases();
    return phases
      .stream()
      .allMatch(phase -> getStrategy(phase).getStatus() == status);
  }

  public boolean anyHaveStatus(Status status, Stage stage) {
    final List<? extends Phase> phases = stage.getPhases();
    return phases
      .stream()
      .anyMatch(phase -> getStrategy(phase).getStatus() == status);
  }

  @Override
  public Status getPhaseStatus(final UUID phaseId) {
    PhaseStrategy strategy = getStrategy(getPhase(phaseId));
    return strategy != null ? strategy.getStatus() : Status.Error;

  }

  @Override
  public List<String> getErrors() {
    return stage.getErrors();
  }

  @Override
  public void update(Observable o, Object obj) {
    if (obj instanceof Protos.TaskStatus) {
      update((Protos.TaskStatus) obj);
    }
  }

  private PhaseStrategy getCurrentPhaseStrategy() {
    Phase phase = getCurrentPhase();
    return phase != null ? getStrategy(phase) : null;
  }

  /**
   * Returns the matching {@link PhaseStrategy} for the provided {@code phaseId}/{@code blockId}.
   */
  private PhaseStrategy getPhaseStrategy(final UUID phaseId, final UUID blockId) {
    Phase phase = getPhase(phaseId);
    if (phase == null) {
      LOGGER.info("Phase not found : phaseId = {}", phaseId);
      return null;
    }
    Block block = phase.getBlock(blockId);
    if (block == null) {
      LOGGER.info("Block not found: phaseId =  {}, blockId = {}",
        phaseId,
        blockId);
      return null;
    }
    PhaseStrategy strategy = getStrategy(phase);
    if (strategy == null) {
      LOGGER.info("Strategy not found: phaseId = {}, blockId = {}",
        phaseId,
        blockId);
      return null;
    }
    return strategy;
  }

  /**
   * Returns a matching {@link PhaseStrategy} for the provided {@link Phase}.
   * If the {@link PhaseStrategy} doesn't exist yet, it is created using {@link PhaseStrategyFactory}.
   */
  protected PhaseStrategy getStrategy(final Phase phase) {
    if (phase == null) {
      LOGGER.warn("null phase");
      return null;
    }
    synchronized (phaseStrategies) {
      if (!phaseStrategies.containsKey(phase.getId())) {
        phaseStrategies.put(
          phase.getId(),
          strategyFactory.getStrategy(phase));
      }
      return phaseStrategies.get(phase.getId());
    }
  }
}
