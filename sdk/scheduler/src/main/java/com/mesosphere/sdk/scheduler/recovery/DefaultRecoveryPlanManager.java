package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.ChainedObserver;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.RandomStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.recovery.RecoveryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager extends ChainedObserver implements PlanManager {
    private static final String RECOVERY_ELEMENT_NAME = "recovery";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConfigStore<ServiceSpec> configStore;

    protected volatile Plan plan;

    protected final StateStore stateStore;
    protected final FailureMonitor failureMonitor;
    protected final LaunchConstrainer launchConstrainer;
    protected final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        plan = new DefaultPlan(RECOVERY_ELEMENT_NAME, Collections.emptyList());
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
            List<String> stepNames = plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .map(step -> step.getName())
                    .collect(Collectors.toList());
            logger.info("Recovery plan set to: {}", stepNames);
        }
    }

    @Override
    public Collection<? extends Step> getCandidates(Collection<String> dirtyAssets) {
        synchronized (planLock) {
            updatePlan(dirtyAssets);
            return PlanUtils.getCandidates(getPlan(), dirtyAssets).stream()
                    .filter(step ->
                            launchConstrainer.canLaunch(((DefaultRecoveryStep) step).getRecoveryType()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Updates the recovery plan if necessary.
     * <p>
     * 1. Updates existing steps.
     * 2. If the needs recovery and doesn't yet have a step in the plan, removes any COMPLETED steps for this task
     * (at most one step for a given task can exist) and creates a new PENDING step.
     *
     * @param status task status
     */
    @Override
    public void update(Protos.TaskStatus status) {
        synchronized (planLock) {
            getPlan().update(status);
            notifyObservers();
        }
    }

    private void updatePlan(Collection<String> dirtyAssets) {
        logger.info("Dirty assets for recovery plan consideration: {}", dirtyAssets);

        synchronized (planLock) {
            // This list will not contain any Complete steps.
            List<Step> steps = null;
            try {
                steps = createSteps(dirtyAssets);
            } catch (TaskException e) {
                logger.error("Failed to generate steps.", e);
                return;
            }

            List<String> stepNames = steps.stream().map(step -> step.getName()).collect(Collectors.toList());
            logger.info("New recovery steps: {}", stepNames);

            List<Step> oldSteps = getPlan().getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> !stepNames.contains(step.getName()))
                    .collect(Collectors.toList());
            logger.info("Old recovery steps: {}",
                    oldSteps.stream().map(step -> step.getName()).collect(Collectors.toList()));

            steps.addAll(oldSteps);
            setPlan(createPlan(steps));
        }
    }

    private Plan createPlan(List<Step> steps) {
        Phase phase = DefaultPhaseFactory.getPhase(RECOVERY_ELEMENT_NAME, steps, new RandomStrategy<>());
        return DefaultPlanFactory.getPlan(RECOVERY_ELEMENT_NAME, Arrays.asList(phase), new SerialStrategy<>());
    }

    List<Step> createSteps(Collection<String> dirtyAssets) throws TaskException {
        Map<PodInstance, List<Protos.TaskInfo>> failedPodsMap =
                TaskUtils.getPodMap(
                        configStore,
                        StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore));

        List<String> podNames = failedPodsMap.keySet().stream()
                .map(podInstance -> podInstance.getName())
                .collect(Collectors.toList());
        logger.info("Found pods needing recovery: " + podNames);

        Predicate<Protos.TaskInfo> isPodPermanentlyFailed = t -> (
                FailureUtils.isLabeledAsFailed(t) || failureMonitor.hasFailed(t));

        List<Step> recoverySteps = new ArrayList<>();
        for (Map.Entry<PodInstance, List<Protos.TaskInfo>> failedPod : failedPodsMap.entrySet()) {

            PodInstance podInstance = failedPod.getKey();
            List<Protos.TaskInfo> failedTasks = failedPod.getValue();

            Integer failedRunningTaskCount = failedTasks.stream()
                    .map(taskInfo -> {
                        try {
                            return TaskUtils.getGoalState(podInstance, taskInfo.getName());
                        } catch (TaskException e) {
                            logger.error("Failed to determine goal state of: {}", taskInfo.getName());
                            return null;
                        }
                    })
                    .filter(goalState -> goalState != null)
                    .filter(goalState -> goalState.equals(GoalState.RUNNING))
                    .collect(Collectors.toList())
                    .size();

            if (dirtyAssets.contains(podInstance.getName())) {
                logger.info("Pod: {} has been dirtied by another plan, cannot recover at this time.",
                        podInstance.getName());
                continue;
            }

            List<TaskSpec> expectedRunningTasks = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                    .collect(Collectors.toList());

            Integer expectedRunningCount = expectedRunningTasks.size();

            logger.info(
                    "Attempting to recover pod tasks: {}",
                    failedTasks.stream().map(taskInfo -> taskInfo.getName()).collect(Collectors.toList()));

            if (!Objects.equals(failedRunningTaskCount, expectedRunningCount)) {
                logger.warn("Pod: '{}' is not recoverable. Failed task count: {}, Expected task count: {}",
                        podInstance.getName(), failedRunningTaskCount, expectedRunningCount);
                continue;
            }

            Collection<String> tasksToLaunch = expectedRunningTasks.stream()
                    .map(taskSpec -> taskSpec.getName())
                    .collect(Collectors.toList());

            // Pods are atomic, even when considering their status as having either permanently or transiently failed.
            // In order for a Pod to be considered permanently failed, all its constituent tasks must have permanently
            // failed.  Otherwise, we will continue to recover from task failures, in place.
            if (failedTasks.stream().allMatch(isPodPermanentlyFailed)) {
                logger.info("Recovering permanently failed pod: '{}'", podInstance.getName());
                recoverySteps.add(new DefaultRecoveryStep(
                        TaskUtils.getStepName(podInstance, tasksToLaunch),
                        Status.PENDING,
                        podInstance,
                        tasksToLaunch,
                        RecoveryType.PERMANENT,
                        launchConstrainer));
            } else if (failedTasks.stream().noneMatch(isPodPermanentlyFailed)) {
                logger.info("Recovering transiently failed pod: '{}'", podInstance.getName());
                recoverySteps.add(new DefaultRecoveryStep(
                        TaskUtils.getStepName(podInstance, tasksToLaunch),
                        Status.PENDING,
                        podInstance,
                        tasksToLaunch,
                        RecoveryType.TRANSIENT,
                        launchConstrainer));
            }
        }

        return recoverySteps;
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        if (plan != null) {
            dirtyAssets.addAll(plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step.isPrepared())
                    .map(step -> step.getName())
                    .collect(Collectors.toSet()));
        }
        return dirtyAssets;
    }
}
