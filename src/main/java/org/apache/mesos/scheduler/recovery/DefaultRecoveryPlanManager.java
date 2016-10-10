package org.apache.mesos.scheduler.recovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.InvalidRequirementException;
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

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 *
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryBlock} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryPlanManager.class);

    protected volatile Plan plan;
    protected volatile PhaseStrategy phaseStrategy;

    private final StateStore stateStore;
    private final TaskFailureListener failureListener;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final UUID phaseId = UUID.randomUUID();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            TaskFailureListener failureListener,
            RecoveryRequirementProvider offerReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.failureListener = failureListener;
    }

    @VisibleForTesting
    protected synchronized void refreshPlan(List<Block> dirtiedAssets) {
        Map<String, Protos.TaskInfo> recoveryCandidates = getRecoveryCandidates(getTerminatedTasks(dirtiedAssets));

        final DefaultPhase.Builder newRecoveryPhaseBuilder = DefaultPhase.builder();
        newRecoveryPhaseBuilder.setId(phaseId);

        final OfferRequirementProvider offerRequirementProvider = new DefaultOfferRequirementProvider();

        // If recovery candidates are not part of a different PlanManager's plan, generate a recovery block for them
        // and mark it as pending. Tasks that are still in terminal state, add them to plan directly
        for (Map.Entry<String, Protos.TaskInfo> candidate : recoveryCandidates.entrySet()) {
            final String name = candidate.getKey();
            final Protos.TaskInfo taskInfo = candidate.getValue();
            try {
                final DefaultTaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
                final List<RecoveryRequirement> recoveryRequirements;
                if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
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
                    final String taskName = block.getName();
                    // Ignore blocks already added to the new plan.
                    if (!recoveryCandidates.containsKey(taskName)) {
                        final Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(taskName);
                        if (taskStatus.isPresent()) {
                            block.update(taskStatus.get());
                            newRecoveryPhaseBuilder.addBlock(block);
                        }
                    }
                }
            }
        }

        final DefaultPhase recoveryPhase = newRecoveryPhaseBuilder.build();
        plan = DefaultPlan.fromArgs(recoveryPhase);
        phaseStrategy = new RandomRecoveryStrategy(recoveryPhase);
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
    public Optional<Block> getCurrentBlock(List<Block> dirtiedAssets) {
        refreshPlan(dirtiedAssets);

        if (phaseStrategy != null) {
            final Optional<Block> chosenBlock = phaseStrategy.getCurrentBlock();
            if (chosenBlock.isPresent() &&
                    launchConstrainer.canLaunch(((DefaultRecoveryBlock) chosenBlock.get()).getRecoveryRequirement())) {
                return Optional.of(chosenBlock.get());
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

    @Override
    public void update(Protos.TaskStatus status) {
        LOGGER.info("Ignoring status: {}", TextFormat.shortDebugString(status));
    }

    @Override
    public boolean hasDecisionPoint(Block block) {
        return phaseStrategy != null && phaseStrategy.hasDecisionPoint(block);
    }

    @Override
    public Status getStatus() {
        // Ordering matters throughout this method.  Modify with care.

        Status result;
        if (!getErrors().isEmpty()) {
            result = Status.ERROR;
            LOGGER.warn("(status={}) Plan contains errors", result);
        } else if (plan == null || CollectionUtils.isEmpty(plan.getPhases())) {
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
        return phaseStrategy != null &&
                phases
                        .stream()
                        .allMatch(phase -> phaseStrategy.getStatus() == status);
    }

    public boolean anyHaveStatus(Status status, Plan plan) {
        final List<? extends Phase> phases = plan.getPhases();
        return phaseStrategy != null &&
                phases
                        .stream()
                        .anyMatch(phase -> phaseStrategy.getStatus() == status);
    }

    @Override
    public Status getPhaseStatus(UUID phaseId) {
        return phaseStrategy != null ? phaseStrategy.getStatus() : Status.COMPLETE;
    }

    @Override
    public List<String> getErrors() {
        return plan != null ? plan.getErrors() : Arrays.asList();
    }

    @Override
    public void update(Observable o, Object arg) {
        LOGGER.info("Ignoring update: {}", arg);
    }

    /**
     * Returns all terminated tasks, excluding those corresponding to {@code block}.  This allows for mutual exclusion
     * with another scheduler.
     *
     * @param dirtiedAssets Blocks with tasks to exclude, empty if no tasks should be excluded
     * @return Terminated tasks, excluding those corresponding to {@code block}
     */
    @VisibleForTesting
    protected Collection<Protos.TaskInfo> getTerminatedTasks(List<Block> dirtiedAssets) {
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

    @VisibleForTesting
    protected Map<String, Protos.TaskInfo> getRecoveryCandidates(Collection<Protos.TaskInfo> terminatedTasks) {
        final Map<String, Protos.TaskInfo> recoveryCandidates = new HashMap<>();

        for (Protos.TaskInfo terminatedTask : terminatedTasks) {
            LOGGER.info("Found stopped task: {}", TextFormat.shortDebugString(terminatedTask));
            if (FailureUtils.isLabeledAsFailed(terminatedTask) || failureMonitor.hasFailed(terminatedTask)) {
                LOGGER.info("Marking stopped task as failed: {}", TextFormat.shortDebugString(terminatedTask));
                failureListener.taskFailed(terminatedTask.getTaskId());
            }
            recoveryCandidates.put(terminatedTask.getName(), terminatedTask);
        }

        return recoveryCandidates;
    }
}
