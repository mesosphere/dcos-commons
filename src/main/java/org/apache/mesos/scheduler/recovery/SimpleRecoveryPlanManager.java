package org.apache.mesos.scheduler.recovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * SimpleRecoveryPlanManager.
 */
public class SimpleRecoveryPlanManager implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleRecoveryPlanManager.class);

    protected volatile Plan plan;

    private final StateStore stateStore;
    private final OfferAccepter offerAccepter;
    private final TaskFailureListener failureListener;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final AtomicReference<RecoveryStatus> recoveryStatusRef;

    public SimpleRecoveryPlanManager(
            StateStore stateStore,
            TaskFailureListener failureListener,
            RecoveryRequirementProvider offerReqProvider,
            OfferAccepter offerAccepter,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            AtomicReference<RecoveryStatus> recoveryStatusRef) {
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.offerAccepter = offerAccepter;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.recoveryStatusRef = recoveryStatusRef;
        this.failureListener = failureListener;
    }

    @VisibleForTesting
    protected synchronized void refreshPlan(List<Block> dirtiedAssets) {
        updateRecoveryStatus(getTerminatedTasks(dirtiedAssets));
        final List<Protos.TaskInfo> stopped = recoveryStatusRef.get().getStopped();
        final List<Protos.TaskInfo> failed = recoveryStatusRef.get().getFailed();
        final Map<String, Protos.TaskInfo> recoveryCandidates = new HashMap<>();
        stopped.stream().forEach(taskInfo -> recoveryCandidates.put(taskInfo.getName(), taskInfo));
        failed.stream().forEach(taskInfo -> recoveryCandidates.put(taskInfo.getName(), taskInfo));

        final DefaultPhase.Builder newRecoveryPhaseBuilder = DefaultPhase.builder();
        final OfferRequirementProvider offerRequirementProvider = new DefaultOfferRequirementProvider();
        // If recovery candidates are not part of plan, generate a recovery block for them and mark it as pending.

        // Tasks that are still in terminal state, add them to plan directly
        for (Map.Entry<String, Protos.TaskInfo> candidate : recoveryCandidates.entrySet()) {
            final String name = candidate.getKey();
            final Protos.TaskInfo taskInfo = candidate.getValue();
            try {
                final DefaultTaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
                final List<RecoveryRequirement> recoveryRequirements;
                if (failureMonitor.hasFailed(taskInfo)) {
                    recoveryRequirements = offerReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
                } else {
                    recoveryRequirements = offerReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
                }
                newRecoveryPhaseBuilder.addBlock(new DefaultRecoveryBlock(
                        taskSpecification.getName(),
                        offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification),
                        Status.PENDING,
                        recoveryRequirements.get(0),
                        launchConstrainer));
            } catch (InvalidTaskSpecificationException | InvalidRequirementException e) {
                LOGGER.error("Error adding recovery plan for task: " + name, e);
            }
        }

        // For blocks in plan, that are not part of recovery candidates, determine their status,
        // using the taskInfo status
        if (plan != null) {
            final List<? extends Phase> phases = plan.getPhases();
            if (CollectionUtils.isNotEmpty(phases)) {
                // Simple plan only deals with a single recovery phase. This makes it explicit.
                final Phase phase = phases.get(0);
                for (Block block : phase.getBlocks()) {
                    final String taskName = getTaskNameFromRecoveryBlock(block.getName());
                    // Ignore blocks already added to the new plan.
                    if (!recoveryCandidates.containsKey(taskName)) {
                        final Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(taskName);
                        if (taskStatus.isPresent()) {
                            block.update(taskStatus.get());
                            if (!block.isComplete()) {
                                newRecoveryPhaseBuilder.addBlock(block);
                            } else {
                                // Remove all the completed blocks from the plan
                                LOGGER.info("Block {} already complete", block.getName());
                            }
                        }
                    }
                }
            }
        }

        plan = DefaultPlan.fromArgs(newRecoveryPhaseBuilder.build());
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
        // TODO: Is this necessary ?
        refreshPlan(Arrays.asList());
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
    public Optional<Block> getCurrentBlock(List<Block> dirtiedAssets) {
        refreshPlan(dirtiedAssets);

        // TODO(gabriel): Allow for pluggable recovery candidate selection strategies.
        final List<? extends Block> blocks = plan.getPhases().get(0).getBlocks();
        final Block chosenBlock = blocks.get(new Random().nextInt(blocks.size()));
        if (launchConstrainer.canLaunch(((DefaultRecoveryBlock) chosenBlock).getRecoveryRequirement())) {
            return Optional.of(chosenBlock);
        }
        // TODO(mohit, gabriel): Handle launch constrainer w.r.t. different task types, Permanent v/s Transient
        return Optional.empty();
    }

    @Override
    public boolean isComplete() {
        return plan != null && plan.isComplete();
    }

    @Override
    public void proceed() {
        // TODO: Do we need to support interrupt for Recovery scheduler ?
        LOGGER.info("Staged execution is unsupported");
    }

    @Override
    public void interrupt() {
        // TODO: Do we need to support interrupt for Recovery scheduler ?
        LOGGER.info("Staged execution is unsupported");
    }

    @Override
    public boolean isInterrupted() {
        // TODO: Do we need to support interrupt for Recovery scheduler ?
        return false;
    }

    @Override
    public void restart(UUID phaseId, UUID blockId) {
        // TODO(mohit): Implementation pending.
        // NOOP
    }

    @Override
    public void forceComplete(UUID phaseId, UUID blockId) {
        // TODO(mohit): Implementation pending.
        // NOOP
    }

    @Override
    public void update(Protos.TaskStatus status) {
        // NOOP
    }

    @Override
    public boolean hasDecisionPoint(Block block) {
        return false;
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
        } else if (anyMatch(block -> block.isInProgress(), plan)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.IN_PROGRESS);
        } else if (allMatch(block -> block.isComplete(), plan)) {
            result = Status.COMPLETE;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.COMPLETE);
        } else if (allMatch(block -> block.isPending(), plan)) {
            result = Status.PENDING;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.PENDING);
        } else if (anyMatch(block -> block.isComplete(), plan) && anyMatch(block -> block.isPending(), plan)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status '{}' and one has status '{}'",
                    result, Status.COMPLETE, Status.PENDING);
        } else {
            result = null;
            LOGGER.error("(status={}) Unexpected state. Plan: {}", result, plan);
        }
        return result;
    }

    public boolean allMatch(final Predicate<Block> predicate, Plan plan) {
        return plan.getPhases().stream().allMatch(phase -> phase.getBlocks().stream().allMatch(predicate));
    }

    public boolean anyMatch(final Predicate<Block> predicate, Plan plan) {
        return plan.getPhases().stream().anyMatch(phase -> phase.getBlocks().stream().anyMatch(predicate));
    }

    @Override
    public Status getPhaseStatus(UUID phaseId) {
        return null;
    }

    @Override
    public List<String> getErrors() {
        return plan != null ? plan.getErrors() : Arrays.asList();
    }

    @Override
    public void update(Observable o, Object arg) {
        // NOOP
    }

    /**
     * Returns all terminated tasks, excluding those corresponding to {@code block}.  This allows for mutual exclusion
     * with another scheduler.
     *
     * @param dirtiedAssets Blocks with tasks to exclude, empty if no tasks should be excluded
     * @return Terminated tasks, excluding those corresponding to {@code block}
     */
    private Collection<Protos.TaskInfo> getTerminatedTasks(List<Block> dirtiedAssets) {
        final List<Protos.TaskInfo> filteredTerminatedTasks = new ArrayList<>();

        try {
            final Collection<Protos.TaskInfo> terminatedTasks = stateStore.fetchTasksNeedingRecovery();
            if (CollectionUtils.isEmpty(dirtiedAssets)) {
                return terminatedTasks;
            }

            for (Block blockToIgnore : dirtiedAssets) {
                final String blockNameToIgnore = blockToIgnore.getName();
                for (Protos.TaskInfo taskForRepair : terminatedTasks) {
                    if (!Objects.equals(taskForRepair.getName(), blockNameToIgnore)) {
                        filteredTerminatedTasks.add(taskForRepair);
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to fetch terminated tasks.", ex);
        }

        return filteredTerminatedTasks;
    }

    private void updateRecoveryStatus(Collection<Protos.TaskInfo> terminatedTasks) {
        List<Protos.TaskInfo> failed = new ArrayList<>(terminatedTasks.stream()
                .filter(failureMonitor::hasFailed)
                .collect(Collectors.toList()));
        failed = failed.stream().distinct().collect(Collectors.toList());

        failed.stream().forEach(it -> failureListener.taskFailed(it.getTaskId()));

        List<Protos.TaskInfo> stopped = terminatedTasks.stream()
                .filter(it -> !failureMonitor.hasFailed(it))
                .collect(Collectors.toList());

        for (Protos.TaskInfo terminatedTask : stateStore.fetchTasksNeedingRecovery()) {
            LOGGER.info("Found stopped task: {}", TextFormat.shortDebugString(terminatedTask));
            if (failureMonitor.hasFailed(terminatedTask)) {
                LOGGER.info("Marking stopped task as failed: {}", TextFormat.shortDebugString(terminatedTask));
            }
        }

        recoveryStatusRef.set(new RecoveryStatus(stopped, failed));
    }

    @VisibleForTesting
    protected static String getTaskNameFromRecoveryBlock(String blockName) {
        return blockName.split(DefaultRecoveryBlock.RECOVERY)[0];
    }
}
