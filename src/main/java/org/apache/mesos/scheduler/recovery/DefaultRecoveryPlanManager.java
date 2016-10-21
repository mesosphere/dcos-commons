package org.apache.mesos.scheduler.recovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

    private final StateStore stateStore;
    private final Strategy strategy;
    private final TaskFailureListener failureListener;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            Strategy strategy,
            TaskFailureListener failureListener,
            RecoveryRequirementProvider offerReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.strategy = strategy;
        this.offerReqProvider = offerReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.failureListener = failureListener;
    }

    @Override
    public Collection<? extends Block> getCandidates(Collection<String> dirtyAssets) {
        refreshPlan(dirtyAssets);
        PlanManager planManager = new DefaultPlanManager(plan);
        Collection<? extends Block> blocks = planManager.getCandidates(dirtyAssets);

        return blocks.stream()
                .filter(block ->  launchConstrainer.canLaunch(((DefaultRecoveryBlock) block).getRecoveryRequirement()))
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    protected synchronized void refreshPlan(Collection<String> dirtiedAssets) {
        Map<String, Protos.TaskInfo> recoveryCandidates = getRecoveryCandidates(getTerminatedTasks(dirtiedAssets));

        final OfferRequirementProvider offerRequirementProvider = new DefaultOfferRequirementProvider();
        List<Block> blocks = new ArrayList<>();

        // If recovery candidates are not part of a different PlanManager's plan, generate a recovery block for them
        // and mark it as pending. Tasks that are still in terminal state, addAll them to plan directly
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

                blocks.add(new DefaultRecoveryBlock(
                        taskSpecification.getName(),
                        offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification),
                        Status.PENDING,
                        recoveryRequirements.get(0),
                        launchConstrainer));
            } catch (InvalidTaskSpecificationException | InvalidRequirementException | TaskException e) {
                LOGGER.error("Error adding recovery plan for task: " + name, e);
            }
        }

        // For blocks in plan, that are not part of recovery candidates, determine their status,
        // using the taskInfo status
        if (plan != null) {
            final List<? extends Element> phases = plan.getChildren();
            if (CollectionUtils.isNotEmpty(phases)) {
                // Simple plan only deals with a single recovery phase. This makes it explicit.
                final Element<Block> phase = phases.get(0);
                for (Block block : phase.getChildren()) {
                    final String taskName = block.getName();
                    // Ignore blocks already added to the new plan.
                    if (!recoveryCandidates.containsKey(taskName)) {
                        final Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(taskName);
                        if (taskStatus.isPresent()) {
                            block.update(taskStatus.get());
                            blocks.add(block);
                        }
                    }
                }
            }
        }

        final DefaultPhase recoveryPhase = new DefaultPhase(
                "Recovery",
                blocks,
                strategy,
                Collections.emptyList());

        DefaultPlan recoveryPlan = new DefaultPlan(
                "Recovery",
                Arrays.asList(recoveryPhase),
                new SerialStrategy(),
                Collections.emptyList());

        plan = recoveryPlan;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    /**
     * Returns all terminated tasks, excluding those corresponding to {@code block}.  This allows for mutual exclusion
     * with another scheduler.
     *
     * @param dirtiedAssets Blocks with tasks to exclude, empty if no tasks should be excluded
     * @return Terminated tasks, excluding those corresponding to {@code block}
     */
    @VisibleForTesting
    protected Collection<Protos.TaskInfo> getTerminatedTasks(Collection<String> dirtiedAssets) {
        final List<Protos.TaskInfo> filteredTerminatedTasks = new ArrayList<>();

        try {
            final Collection<Protos.TaskInfo> terminatedTasks = stateStore.fetchTasksNeedingRecovery();
            if (CollectionUtils.isEmpty(dirtiedAssets)) {
                return terminatedTasks;
            }

            for (String assetToIgnore : dirtiedAssets) {
                for (Protos.TaskInfo taskForRepair : terminatedTasks) {
                    if (!Objects.equals(taskForRepair.getName(), assetToIgnore)) {
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
