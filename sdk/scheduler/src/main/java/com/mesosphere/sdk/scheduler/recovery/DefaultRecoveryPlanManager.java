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
    protected static final String RECOVERY_ELEMENT_NAME = "recovery";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ConfigStore<ServiceSpec> configStore;
    private final List<PlanManager> overrideRecoveryManagers;

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
        this(stateStore, configStore, launchConstrainer, failureMonitor, Collections.emptyList());
    }

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            List<PlanManager> overrideRecoveryManagers) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.overrideRecoveryManagers = overrideRecoveryManagers;
        plan = new DefaultPlan(RECOVERY_ELEMENT_NAME, Collections.emptyList());
    }

    @Override
    public Plan getPlan() {
        synchronized (planLock) {
            return plan;
        }
    }

    protected void setPlan(Plan plan) {
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
    public Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets) {
        synchronized (planLock) {
            updatePlan(dirtyAssets);
            return getPlan().getCandidates(dirtyAssets).stream()
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

    protected void updatePlan(Collection<PodInstanceRequirement> dirtyAssets) {
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
        return DeployPlanFactory.getPlan(RECOVERY_ELEMENT_NAME, Arrays.asList(phase), new SerialStrategy<>());
    }

    private List<PodInstanceRequirement> getRecoveryRequirements(Collection<PodInstanceRequirement> dirtyAssets)
            throws TaskException {

        Collection<Protos.TaskInfo> failedTasks = StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
        List<PodInstanceRequirement> failedPods = TaskUtils.getPodMap(
                configStore,
                failedTasks,
                stateStore.fetchTasks());

        List<String> podNames = failedPods.stream()
                .map(podInstanceRequirement -> podInstanceRequirement.getPodInstance().getName())
                .collect(Collectors.toList());
        logger.info("Found pods needing recovery: " + podNames);

        Predicate<Protos.TaskInfo> isPodPermanentlyFailed = t -> (
                FailureUtils.isLabeledAsFailed(t) || failureMonitor.hasFailed(t));

        List<PodInstanceRequirement> recoveryRequirements = new ArrayList<>();
        for (PodInstanceRequirement failedPod : failedPods) {
            List<Protos.TaskInfo> failedPodTaskInfos = failedPod.getTasksToLaunch().stream()
                    .map(taskSpecName -> TaskSpec.getInstanceName(failedPod.getPodInstance(), taskSpecName))
                    .map(taskInfoName -> stateStore.fetchTask(taskInfoName))
                    .filter(taskInfo -> taskInfo.isPresent())
                    .map(taskInfo -> taskInfo.get())
                    .collect(Collectors.toList());

            // Pods are atomic, even when considering their status as having either permanently or transiently failed.
            // In order for a Pod to be considered permanently failed, all its constituent tasks must have permanently
            // failed.  Otherwise, we will continue to recover from task failures, in place.
            PodInstanceRequirement podInstanceRequirement = null;
            RecoveryType recoveryType = null;
            if (failedPodTaskInfos.stream().allMatch(isPodPermanentlyFailed)) {
                logger.info("Recovering permanently failed pod: '{}'", failedPod);
                recoveryType = RecoveryType.PERMANENT;
                podInstanceRequirement = PodInstanceRequirement.createPermanentReplacement(failedPod);
            } else if (failedPodTaskInfos.stream().noneMatch(isPodPermanentlyFailed)) {
                logger.info("Recovering transiently failed pod: '{}'", failedPod);
                recoveryType = RecoveryType.TRANSIENT;
                podInstanceRequirement = PodInstanceRequirement.createTransientRecovery(failedPod);
            } else {
                logger.error("Tasks have failed in a mixture of transient and permanent states and cannot be processed");
                continue;
            }

            if (PlanUtils.assetConflicts(podInstanceRequirement, dirtyAssets)) {
                logger.info("Pod: {} has been dirtied by another plan, cannot recover at this time.", failedPod);
            } else {
                recoveryRequirements.add(podInstanceRequirement);
            }
        }

        return recoveryRequirements;
    }

    List<Step> createSteps(Collection<PodInstanceRequirement> dirtyAssets) throws TaskException {
        List<Step> recoverySteps = new ArrayList<>();
        for (PodInstanceRequirement recoveryRequirement : getRecoveryRequirements(dirtyAssets)) {
            logger.info("Attempting to recover: {}", recoveryRequirement);
            recoverySteps.add(new DefaultRecoveryStep(
                    recoveryRequirement.toString(),
                    Status.PENDING,
                    recoveryRequirement,
                    launchConstrainer,
                    stateStore));
        }

        for (PlanManager ovverrideRecoveryManager : overrideRecoveryManagers) {
            Collection<? extends Step> ovverrideSteps = ovverrideRecoveryManager.getCandidates(dirtyAssets);
            recoverySteps = override(recoverySteps, ovverrideSteps);
        }

        return recoverySteps;
    }

    @Override
    public Set<PodInstanceRequirement> getDirtyAssets() {
        Set<PodInstanceRequirement> dirtyAssets = new HashSet<>();
        if (plan != null) {
            dirtyAssets.addAll(plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step.isPrepared())
                    .map(step -> step.getPodInstanceRequirement())
                    .filter(podInstanceRequirement -> podInstanceRequirement.isPresent())
                    .map(podInstanceRequirement -> podInstanceRequirement.get())
                    .collect(Collectors.toSet()));
        }
        return dirtyAssets;
    }

    private List<Step> override(List<Step> oldSteps, Collection<? extends Step> newSteps) {
        List<Step> filteredSteps = new ArrayList<>();
        oldSteps = oldSteps.stream()
                .filter(step -> step.getPodInstanceRequirement().isPresent())
                .collect(Collectors.toList());

        for (Step oldStep : oldSteps) {
            PodInstance oldPodInstance = oldStep.getPodInstanceRequirement().get().getPodInstance();
            boolean overridden = newSteps.stream()
                    .filter(step -> step.getPodInstanceRequirement().isPresent())
                    .map(step -> step.getPodInstanceRequirement().get().getPodInstance())
                    .filter(podInstance -> PlanUtils.podInstancesConflict(podInstance, oldPodInstance))
                    .count() > 0;

            if (!overridden) {
                filteredSteps.add(oldStep);
            } else {
                logger.info("Step {} is overriden.", oldStep);
            }
        }

        filteredSteps.addAll(newSteps);
        return filteredSteps;
    }
}
