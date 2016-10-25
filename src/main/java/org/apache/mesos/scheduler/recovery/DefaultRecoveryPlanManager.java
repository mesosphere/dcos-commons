package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.*;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryBlock} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager extends ChainedObserver implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryPlanManager.class);

    protected volatile Plan plan;
    protected volatile PhaseStrategy phaseStrategy;
    protected volatile Map<UUID, PhaseStrategy> phaseStrategies;

    private final StateStore stateStore;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final UUID phaseId = UUID.randomUUID();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            RecoveryRequirementProvider offerReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        updatePlan(Collections.emptyList());
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public void setPlan(Plan plan) {
        LOGGER.info("Ignoring setPlan call. {} generates plans dynamically.", getClass().getName());
    }

    /**
     * Returns the first {@link Phase} in the {@link Plan} which isn't marked complete.
     */
    @Override
    public Optional<Phase> getCurrentPhase() {
        for (final Phase phase : plan.getPhases()) {
            if (!phase.isComplete()) {
                LOGGER.debug("Phase {} ({}) is NOT complete. This is the current phase.",
                        phase.getName(), phase.getId());
                return Optional.of(phase);
            } else {
                LOGGER.debug("Phase {} ({}) is complete.", phase.getName(), phase.getId());
            }
        }
        LOGGER.debug("All recovery phases are complete.");
        return Optional.empty();
    }

    @Override
    public Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets) {
        if (phaseStrategy != null) {
            final Optional<Block> chosenBlock = phaseStrategy.getCurrentBlock(dirtiedAssets);
            if (chosenBlock.isPresent() &&
                    launchConstrainer.canLaunch(((DefaultRecoveryBlock) chosenBlock.get()).getRecoveryRequirement())) {
                return chosenBlock;
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean isComplete() {
        return plan != null && plan.isComplete();
    }

    @Override
    public void proceed() {
        LOGGER.info("Resuming recovery...");
        if (phaseStrategy != null) {
            phaseStrategy.proceed();
            LOGGER.info("Done resuming recovery for phase: {}", phaseStrategy.getPhase());
        } else {
            LOGGER.info("No phase to resume");
        }
    }

    @Override
    public void interrupt() {
        LOGGER.info("Interrupting recovery");
        if (phaseStrategy != null) {
            phaseStrategy.interrupt();
            LOGGER.info("Interrupted current phase: phase = {}", phaseStrategy.getPhase());
        } else {
            LOGGER.info("No phase to interrupt");
        }
    }

    @Override
    public boolean isInterrupted() {
        return getStatus() == Status.WAITING;
    }

    @Override
    public void restart(UUID phaseId, UUID blockId) {
        if (phaseStrategy != null) {
            phaseStrategy.restart(blockId);
        }
    }

    @Override
    public void forceComplete(UUID phaseId, UUID blockId) {
        if (phaseStrategy != null) {
            phaseStrategy.forceComplete(blockId);
        }
    }

    /**
     * Updates the recovery plan if necessary.
     * <p>
     * 1. Updates existing blocks.
     * 2. If the needs recovery and doesn't yet have a block in the plan, removes any COMPLETED blocks for this task
     * (at most one block for a given task can exist) and creates a new PENDING block.
     *
     * @param status task status
     */
    @Override
    public void update(Protos.TaskStatus status) {
        try {
            String taskName = TaskUtils.toTaskName(status.getTaskId());

            Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(taskName);
            if (!taskInfo.isPresent()) {
                LOGGER.error("Failed to fetch TaskInfo for task (name={})", taskName);
                return;
            }

            // 1. Update existing blocks.
            this.plan.getPhases().forEach(
                    phase -> phase.getBlocks().forEach(
                            block -> block.update(status)));

            boolean blockExists = this.plan.getPhases().stream()
                    .filter(phase -> phase.getBlocks().stream()
                            .filter(block -> block.getName().equals(taskName)).findAny().isPresent())
                    .findAny().isPresent();

            if (!blockExists && TaskUtils.needsRecovery(status)) {
                Optional<Block> newBlock = createBlock(taskInfo.get());
                if (newBlock.isPresent()) {
                    Collection<Block> blocks = new ArrayList<>();

                    // 2. remove any COMPLETED blocks for this task.
                    for (Phase phase : this.plan.getPhases()) {
                        blocks.addAll(phase.getBlocks().stream()
                                .filter(block -> !(block.isComplete() && block.getName().equals(taskName)))
                                .collect(Collectors.toList()));
                    }

                    // 3. Add new PENDING block.
                    blocks.add(newBlock.get());

                    updatePlan(blocks);
                }
            }
        } catch (TaskException ex) {
            LOGGER.error("Error updating status for task (id={})", status.getTaskId().toString(), ex);
        }
    }

    @Override
    public boolean hasDecisionPoint(Block block) {
        return phaseStrategy != null && phaseStrategy.hasDecisionPoint(block);
    }

    @Override
    public Status getStatus() {
        return PlanManagerUtils.getStatus(getPlan(), phaseStrategies);
    }

    @Override
    public Status getPhaseStatus(UUID phaseId) {
        return phaseStrategy != null ? phaseStrategy.getStatus() : Status.COMPLETE;
    }

    @Override
    public List<String> getErrors() {
        return plan != null ? plan.getErrors() : Arrays.asList();
    }

    private void updatePlan(Collection<Block> blocks) {
        Phase phase = DefaultPhase.builder()
                .setId(phaseId)
                .addBlocks(blocks)
                .build();

        plan = DefaultPlan.fromArgs(phase);
        plan.subscribe(this);

        phaseStrategy = new RandomRecoveryStrategy(phase);
        phaseStrategies = new HashMap<>();
        phaseStrategies.put(phase.getId(), phaseStrategy);
        this.notifyObservers();
    }

    private Optional<Block> createBlock(Protos.TaskInfo taskInfo) {
        try {
            final OfferRequirementProvider offerRequirementProvider = new DefaultOfferRequirementProvider();
            final TaskSpecification taskSpec = DefaultTaskSpecification.create(taskInfo);
            final List<RecoveryRequirement> recoveryRequirements;

            if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
                recoveryRequirements = offerReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
            } else {
                recoveryRequirements = offerReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
            }

            return Optional.of(new DefaultRecoveryBlock(
                    taskSpec.getName(),
                    offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpec),
                    Status.PENDING,
                    recoveryRequirements.get(0),
                    launchConstrainer));
        } catch (InvalidTaskSpecificationException | InvalidRequirementException ex) {
            LOGGER.error("Error creating recovery block.", ex);
            return Optional.empty();
        }
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        if (plan != null) {
            final List<? extends Phase> phases = plan.getPhases();
            for (Phase phase : phases) {
                final List<? extends Block> blocks = phase.getBlocks();
                for (Block block : blocks) {
                    if (block.isInProgress()) {
                        dirtyAssets.add(block.getName());
                    }
                }
            }
        }
        return dirtyAssets;
    }
}
