package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;

import java.util.*;

/**
 * Provides the default implementation of a {@link StageManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultStageManager implements StageManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStageManager.class);

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
        LOGGER.debug("Phase {} ({}) is NOT complete. This is the current phase.",
                phase.getName(), phase.getId());
        return phase;
      } else {
        LOGGER.debug("Phase {} ({}) is complete.", phase.getName(), phase.getId());
      }
    }
    LOGGER.debug("All phases are complete.");
    return null;
  }

  @Override
  public Block getCurrentBlock() {
    PhaseStrategy currPhase = getCurrentPhaseStrategy();
    return (currPhase != null) ? currPhase.getCurrentBlock() : null;
  }

  @Override
  public boolean isComplete() {
    return stage.isComplete();
  }

  @Override
  public void proceed() {
    LOGGER.info("Proceeding with staged execution");
    final PhaseStrategy currPhase = getCurrentPhaseStrategy();
    if (currPhase != null) {
      currPhase.proceed();
      LOGGER.info("Proceeding with current phase: phase = {}", currPhase);
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
    LOGGER.info("Restarting phase : phaseId = {}, blockId = {}", phaseId, blockId);
    PhaseStrategy strategy = getPhaseStrategy(phaseId, blockId);
    if (strategy == null) {
      return;
    }
    strategy.restart(blockId);
    LOGGER.info("Restarted phase : phaseId = {}, blockId = {}", phaseId, blockId);
  }

  @Override
  public void forceComplete(final UUID phaseId, final UUID blockId) {
    LOGGER.info("Forcing completion : phaseId = {}, blockId = {}", phaseId, blockId);
    PhaseStrategy strategy = getPhaseStrategy(phaseId, blockId);
    if (strategy == null) {
      return;
    }
    strategy.forceComplete(blockId);
    LOGGER.info("Forced completion : phaseId = {}, blockId = {}", phaseId, blockId);
  }

  @Override
  public void update(final Protos.TaskStatus status) {
    LOGGER.info("Received status update : status = {}", TextFormat.shortDebugString(status));

    final PhaseStrategy currentPhaseStrategy = getCurrentPhaseStrategy();
    if (currentPhaseStrategy != null) {
      final Phase currentPhase = currentPhaseStrategy.getPhase();
      if (currentPhase != null) {
        final List<? extends Block> blocks = currentPhase.getBlocks();
        for (Block block : blocks) {
          LOGGER.info("Updating block '{}' with status", block.getName());
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
    UUID phaseId = getPhaseId(block);
    if (phaseId != null) {
      return getPhaseStrategy(phaseId, block.getId()).hasDecisionPoint(block);
    } else {
      return false;
    }
  }

  private UUID getPhaseId(final Block block) {
    for (Phase phase : stage.getPhases()) {
      for (Block blk : phase.getBlocks()) {
        if (block.getId().equals(blk.getId())) {
          return phase.getId();
        }
      }
    }

    return null;
  }

  @Override
  public Status getStatus() {
    // Ordering matters throughout this method.  Modify with care.

    Status result;
    if (!getErrors().isEmpty()) {
      result = Status.Error;
      LOGGER.warn("(status={}) Stage contains errors", result);
    } else if (stage.getPhases().isEmpty()) {
      result = Status.Complete;
      LOGGER.warn("(status={}) Stage doesn't have any phases", result);
    } else if (anyHaveStatus(Status.InProgress, stage)) {
      result = Status.InProgress;
      LOGGER.info("(status={}) At least one phase has status: {}", result, Status.InProgress);
    } else if (anyHaveStatus(Status.Waiting, stage)) {
      result = Status.Waiting;
      LOGGER.info("(status={}) At least one phase has status: {}", result, Status.Waiting);
    } else if (allHaveStatus(Status.Complete, stage)) {
      result = Status.Complete;
      LOGGER.info("(status={}) All phases have status: {}", result, Status.Complete);
    } else if (allHaveStatus(Status.Pending, stage)) {
      result = Status.Pending;
      LOGGER.info("(status={}) All phases have status: {}", result, Status.Pending);
    } else if (anyHaveStatus(Status.Complete, stage) && anyHaveStatus(Status.Pending, stage)) {
      result = Status.InProgress;
      LOGGER.info("(status={}) At least one phase has status '{}' and one has status '{}'",
              result, Status.Complete, Status.Pending);
    } else {
      result = null;
      LOGGER.error("(status={}) Unexpected state. Stage: {}", result, stage);
    }
    return result;
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

  /**
   * Returns the {@link Phase} which has the provided {@code phaseId}, or {@code null} if no
   * matching {@link Phase} was found.
   */
  protected Phase getPhase(final UUID phaseId) {
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
}
