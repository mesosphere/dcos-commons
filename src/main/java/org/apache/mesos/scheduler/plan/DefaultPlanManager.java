package org.apache.mesos.scheduler.plan;

import com.google.protobuf.TextFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager extends ChainedObserver implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanManager.class);

    protected final Map<UUID, PhaseStrategy> phaseStrategies = new HashMap<>();
    protected volatile Plan plan;

    public DefaultPlanManager(final Plan plan,
                              final PhaseStrategyFactory strategyFactory) {
        setPlan(plan);
        final List<? extends Phase> phases = plan.getPhases();
        for (Phase phase : phases) {
            phaseStrategies.put(phase.getId(), strategyFactory.getStrategy(phase));
        }
    }

    @Override
    public void setPlan(final Plan plan) {
        LOGGER.info("Setting plan : state = {}", plan);
        this.plan = plan;
        this.plan.subscribe(this);
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
    public Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets) {
        Optional<PhaseStrategy> currPhaseOptional = getCurrentPhaseStrategy();
        if (currPhaseOptional.isPresent()) {
            final Optional<Block> currentBlock = currPhaseOptional.get().getCurrentBlock(dirtiedAssets);
            if (currentBlock.isPresent()) {
                if (CollectionUtils.isNotEmpty(dirtiedAssets)) {
                    final Block block = currentBlock.get();
                    for (String dirtyAsset : dirtiedAssets) {
                        if (Objects.equals(dirtyAsset, block.getName())) {
                            LOGGER.info("Chosen block is already dirtied by other PlanManager. No block to schedule.");
                            return Optional.empty();
                        }
                    }
                    LOGGER.info("Chosen block is not dirtied by other PlanManager. Returning block: {}",
                            currentBlock.get().getName());
                    return currentBlock;
                } else {
                    LOGGER.info("There are no dirty assets. Returning block: {}", currentBlock.get().getName());
                    return currentBlock;
                }
            } else {
                LOGGER.info("No block to schedule.");
                return Optional.empty();
            }
        } else {
            LOGGER.info("No phase to schedule.");
            return Optional.empty();
        }
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
        return PlanManagerUtils.getStatus(getPlan(), phaseStrategies);
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
        return phaseStrategies.get(phase.getId());
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
