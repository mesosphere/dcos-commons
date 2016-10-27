package org.apache.mesos.scheduler.recovery;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.RandomStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.specification.TaskSpecificationProvider;
import org.apache.mesos.state.StateStore;

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
    private static final String RECOVERY_ELEMENT_NAME = "recovery";

    protected volatile Plan plan;

    private final StateStore stateStore;
    private final TaskSpecificationProvider taskSpecificationProvider;
    private final OfferRequirementProvider offerReqProvider;
    private final RecoveryRequirementProvider recoveryReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            TaskSpecificationProvider taskSpecificationProvider,
            OfferRequirementProvider offerReqProvider,
            RecoveryRequirementProvider recoveryReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.taskSpecificationProvider = taskSpecificationProvider;
        this.offerReqProvider = offerReqProvider;
        this.recoveryReqProvider = recoveryReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        setPlan(createPlan(createBlocks()));
    }

    @Override
    public Plan getPlan() {
        synchronized (planLock) {
            return plan;
        }
    }

    private void setPlan(Plan plan) {
        synchronized (planLock) {
            this.plan = plan;
            this.plan.subscribe(this);
        }
    }

    @Override
    public Collection<? extends Block> getCandidates(Collection<String> dirtyAssets) {
        synchronized (planLock) {
            updatePlan();
            return PlanUtils.getCandidates(getPlan(), dirtyAssets).stream()
                    .filter(block ->
                            launchConstrainer.canLaunch(((DefaultRecoveryBlock) block).getRecoveryRequirement()))
                    .collect(Collectors.toList());
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
        synchronized (planLock) {
            getPlan().update(status);
            updatePlan();
            notifyObservers();
        }
    }
    private void updatePlan() {
        synchronized (planLock) {
            // This list will not contain any Complete blocks.
            List<Block> blocks = createBlocks();
            List<String> blockNames = blocks.stream().map(block -> block.getName()).collect(Collectors.toList());
            List<Block> completeBlocks = getPlan().getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(block -> !blockNames.contains(block.getName()))
                    .collect(Collectors.toList());

            blocks.addAll(completeBlocks);
            setPlan(createPlan(blocks));
        }
    }

    private Plan createPlan(List<Block> blocks) {
        Phase phase = DefaultPhaseFactory.getPhase(RECOVERY_ELEMENT_NAME, blocks, new RandomStrategy<>());
        return DefaultPlanFactory.getPlan(RECOVERY_ELEMENT_NAME, Arrays.asList(phase), new SerialStrategy<>());
    }

    private List<Block> createBlocks() {
        return stateStore.fetchTasksNeedingRecovery().stream()
                .map(taskInfo -> {
                    try {
                        return createBlock(
                                TaskUtils.unpackTaskInfo(taskInfo),
                                taskSpecificationProvider.getTaskSpecification(taskInfo));
                    } catch (
                            TaskException |
                            InvalidRequirementException |
                            InvalidProtocolBufferException e) {
                        return new DefaultBlock(
                                taskInfo.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                Arrays.asList(ExceptionUtils.getStackTrace(e)));
                    }
                })
                .collect(Collectors.toList());
    }

    private Block createBlock(Protos.TaskInfo taskInfo, TaskSpecification taskSpec)
            throws TaskException, InvalidRequirementException {
        final List<RecoveryRequirement> recoveryRequirements;

        if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
            recoveryRequirements = recoveryReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
        } else {
            recoveryRequirements = recoveryReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
        }

        return new DefaultRecoveryBlock(
                taskInfo.getName(),
                offerReqProvider.getExistingOfferRequirement(taskInfo, taskSpec),
                Status.PENDING,
                recoveryRequirements.get(0),
                launchConstrainer);
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        if (plan != null) {
            dirtyAssets.addAll(plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(block -> block.isInProgress())
                    .map(block -> block.getName())
                    .collect(Collectors.toSet()));
        }
        return dirtyAssets;
    }
}
