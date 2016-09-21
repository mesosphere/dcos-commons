package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;

import java.util.*;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanManager.class);

    /**
     * Access to {@code phaseStrategies} MUST be locked/synchronized against
     * {@code phaseStrategies}.
     */
    protected final Map<UUID, PhaseStrategy> phaseStrategies = new HashMap<>();
    protected volatile Plan plan;

    private final PhaseStrategyFactory strategyFactory;

    public DefaultPlanManager(final Plan plan,
                              final PhaseStrategyFactory
                                      strategyFactory) {
        this.strategyFactory = strategyFactory;
        setPlan(plan);
    }

    @Override
    public void setPlan(final Plan plan) {
        LOGGER.info("Setting plan : state = {}", plan);
        this.plan = plan;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    /**
     * Returns the first {@link Phase} in the {@link Plan} which isn't marked complete.
     */
    @Override
    public Optional<Phase> getCurrentPhase() {
        for (Phase phase : plan.getPhases()) {
            if (!phase.isComplete()) {
                LOGGER.debug("Phase {} ({}) is NOT complete. This is the current phase.",
                        phase.getName(), phase.getId());
                return Optional.of(phase);
            } else {
                LOGGER.debug("Phase {} ({}) is complete.", phase.getName(), phase.getId());
            }
        }
        LOGGER.debug("All phases are complete.");
        return Optional.empty();
    }

    @Override
    public Optional<Block> getCurrentBlock() {
        Optional<PhaseStrategy> currPhaseOptional = getCurrentPhaseStrategy();
        return currPhaseOptional.isPresent() ?
                currPhaseOptional.get().getCurrentBlock() : Optional.empty();
    }

    @Override
    public boolean isComplete() {
        return plan.isComplete();
    }

    @Override
    public void proceed() {
        LOGGER.info("Proceeding with staged execution");
        final Optional<PhaseStrategy> currPhaseOptional = getCurrentPhaseStrategy();
        if (currPhaseOptional.isPresent()) {
            currPhaseOptional.get().proceed();
            LOGGER.info("Proceeding with current phase: phase = {}", currPhaseOptional.get());
        } else {
            LOGGER.info("No phase to proceed");
        }
    }

    @Override
    public void interrupt() {
        LOGGER.info("Interrupting staged execution");
        final Optional<PhaseStrategy> currPhaseOptional = getCurrentPhaseStrategy();
        if (currPhaseOptional.isPresent()) {
            currPhaseOptional.get().interrupt();
            LOGGER.info("Interrupted current phase: phase = {}", currPhaseOptional.get());
        } else {
            LOGGER.info("No phase to interrupt");
        }
    }

    @Override
    public boolean isInterrupted() {
        return getStatus() == Status.WAITING;
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
        LOGGER.debug("Received status update : status = {}", TextFormat.shortDebugString(status));
        final Optional<PhaseStrategy> currPhaseStrategyOptional = getCurrentPhaseStrategy();
        if (currPhaseStrategyOptional.isPresent()) {
            final Phase currentPhase = currPhaseStrategyOptional.get().getPhase();
            if (currentPhase != null) {
                final List<? extends Block> blocks = currentPhase.getBlocks();
                for (Block block : blocks) {
                    LOGGER.debug("Updating block '{}' with status", block.getName());
                    block.update(status);
                }
            } else {
                LOGGER.debug("currentPhase is null. No blocks to receive status");
            }
        } else {
            LOGGER.debug("currentPhaseStrategy is not present. No blocks to receive status");
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
        for (Phase phase : plan.getPhases()) {
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
            result = Status.ERROR;
            LOGGER.warn("(status={}) Plan contains errors", result);
        } else if (plan.getPhases().isEmpty()) {
            result = Status.COMPLETE;
            LOGGER.warn("(status={}) Plan doesn't have any phases", result);
        } else if (anyHaveStatus(Status.IN_PROGRESS, plan)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.IN_PROGRESS);
        } else if (anyHaveStatus(Status.WAITING, plan)) {
            result = Status.WAITING;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.WAITING);
        } else if (allHaveStatus(Status.COMPLETE, plan)) {
            result = Status.COMPLETE;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.COMPLETE);
        } else if (allHaveStatus(Status.PENDING, plan)) {
            result = Status.PENDING;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.PENDING);
        } else if (anyHaveStatus(Status.COMPLETE, plan) && anyHaveStatus(Status.PENDING, plan)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status '{}' and one has status '{}'",
                    result, Status.COMPLETE, Status.PENDING);
        } else {
            result = null;
            LOGGER.error("(status={}) Unexpected state. Plan: {}", result, plan);
        }
        return result;
    }

    public boolean allHaveStatus(Status status, Plan plan) {
        final List<? extends Phase> phases = plan.getPhases();
        return phases
                .stream()
                .allMatch(phase -> getStrategy(phase).getStatus() == status);
    }

    public boolean anyHaveStatus(Status status, Plan plan) {
        final List<? extends Phase> phases = plan.getPhases();
        return phases
                .stream()
                .anyMatch(phase -> getStrategy(phase).getStatus() == status);
    }

    @Override
    public Status getPhaseStatus(final UUID phaseId) {
        PhaseStrategy strategy = getStrategy(getPhase(phaseId));
        return strategy != null ? strategy.getStatus() : Status.ERROR;
    }

    @Override
    public List<String> getErrors() {
        return plan.getErrors();
    }

    @Override
    public void update(Observable o, Object obj) {
        if (obj instanceof Protos.TaskStatus) {
            update((Protos.TaskStatus) obj);
        }
    }

    private Optional<PhaseStrategy> getCurrentPhaseStrategy() {
        Optional<Phase> phaseOptional = getCurrentPhase();
        return phaseOptional.isPresent() ? Optional.of(getStrategy(phaseOptional.get())) : Optional.empty();
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

        for (Phase phase : plan.getPhases()) {
            if (phaseId.equals(phase.getId())) {
                return phase;
            }
        }

        return null;
    }
}
