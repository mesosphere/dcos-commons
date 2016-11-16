package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.RandomStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.ServiceSpec;
import org.apache.mesos.specification.TaskSpec;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreUtils;
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
    protected final RecoveryRequirementProvider recoveryReqProvider;
    protected final FailureMonitor failureMonitor;
    protected final LaunchConstrainer launchConstrainer;
    protected final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            RecoveryRequirementProvider recoveryReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.recoveryReqProvider = recoveryReqProvider;
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
            List<String> stepNames =  plan.getChildren().stream()
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
                            launchConstrainer.canLaunch(((DefaultRecoveryStep) step).getRecoveryRequirement()))
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
                        StateStoreUtils.fetchTasksNeedingRecovery(stateStore));

        logger.info("Found pods needing recovery: {}", failedPodsMap.keySet());

        Predicate<Protos.TaskInfo> isPodPermanentlyFailed = t -> (
                FailureUtils.isLabeledAsFailed(t) || failureMonitor.hasFailed(t));

        List<RecoveryRequirement> recoveryRequirements = new ArrayList<>();
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
                    .filter(goalState -> goalState.equals(TaskSpec.GoalState.RUNNING))
                    .collect(Collectors.toList())
                    .size();

            Integer expectedRunningCount = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                    .collect(Collectors.toList())
                    .size();

            Predicate<Protos.TaskInfo> isPodRecoverable = t -> {
                return !dirtyAssets.contains(t.getName());
            };

            logger.info(
                    "Attempting to recover pod tasks: {}",
                    failedTasks.stream().map(taskInfo -> taskInfo.getName()).collect(Collectors.toList()));

            if (failedRunningTaskCount != expectedRunningCount || !failedTasks.stream().allMatch(isPodRecoverable)) {
                logger.warn("Pod: '{}' is not recoverable. Failed task count: {}, Expected task count: {}",
                        podInstance.getName(), failedRunningTaskCount, expectedRunningCount);
                continue;
            }

            try {
                if (failedTasks.stream().allMatch(isPodPermanentlyFailed)) {
                    logger.info("Recovering permanently failed pod: '{}'", podInstance.getName());
                    recoveryRequirements.addAll(
                            recoveryReqProvider.getPermanentRecoveryRequirements(failedTasks));
                } else if (failedTasks.stream().noneMatch(isPodPermanentlyFailed)) {
                    logger.info("Recovering transiently failed pod: '{}'", podInstance.getName());
                    recoveryRequirements.addAll(
                            recoveryReqProvider.getTransientRecoveryRequirements(failedTasks));
                }
            } catch (InvalidRequirementException e) {
                logger.error("Failed to generate recovery requirement for pod: '{}'", podInstance.getName(), e);
                continue;
            }
        }

        return createSteps(recoveryRequirements);
    }

    private List<Step> createSteps(List<RecoveryRequirement> recoveryRequirements) {
        return recoveryRequirements.stream()
                .map(recoveryRequirement -> new DefaultRecoveryStep(
                        recoveryRequirement.getPodInstance().getName(),
                        Status.PENDING,
                        recoveryRequirement.getPodInstance(),
                        recoveryRequirement,
                        launchConstrainer))
                .collect(Collectors.toList());
    }

    private List<Step> createSteps(Protos.TaskInfo taskInfo)
            throws TaskException, InvalidRequirementException {
        final List<RecoveryRequirement> recoveryRequirements;

        if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
            recoveryRequirements = recoveryReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
        } else {
            recoveryRequirements = recoveryReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
        }

        return recoveryRequirements.stream()
                .map(recoveryRequirement -> new DefaultRecoveryStep(
                        taskInfo.getName(),
                        Status.PENDING,
                        recoveryRequirement.getPodInstance(),
                        recoveryRequirement,
                        launchConstrainer))
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        if (plan != null) {
            dirtyAssets.addAll(plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step.isInProgress())
                    .map(step -> step.getName())
                    .collect(Collectors.toSet()));
        }
        return dirtyAssets;
    }
}
