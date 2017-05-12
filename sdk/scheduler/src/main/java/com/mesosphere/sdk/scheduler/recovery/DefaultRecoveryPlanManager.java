package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.ChainedObserver;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import com.mesosphere.sdk.scheduler.Observable;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager extends ChainedObserver implements PlanManager {
    public static final String DEFAULT_RECOVERY_PLAN_NAME = "recovery";
    public static final String DEFAULT_RECOVERY_PHASE_NAME = "default";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ConfigStore<ServiceSpec> configStore;
    private final List<RecoveryPlanOverrider> recoveryPlanOverriders;

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
            List<RecoveryPlanOverrider> overrideRecoveryManagers) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.recoveryPlanOverriders = overrideRecoveryManagers;
        plan = new DefaultPlan(DEFAULT_RECOVERY_PLAN_NAME, Collections.emptyList());
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
            Collection<PodInstanceRequirement> podInstanceRequirements = null;

            try {
                podInstanceRequirements = getRecoveryRequirements(dirtyAssets);
            } catch (TaskException e) {
                logger.error("Failed to generate steps.", e);
                return;
            }

            List<PodInstanceRequirement> defaultRequirements = new ArrayList<>();
            List<Phase> phases = new ArrayList<>();
            for (PodInstanceRequirement requirement : podInstanceRequirements) {
                boolean overriden = false;
                for (RecoveryPlanOverrider overrider : recoveryPlanOverriders) {
                    Optional<Phase> override  = overrider.override(requirement);
                    if (override.isPresent()) {
                        overriden = true;
                        Phase phase = override.get();
                        phases.add(phase);
                    }
                }

                if (!overriden) {
                    defaultRequirements.add(requirement);
                }
            }

            Plan plan = createPlan(defaultRequirements, phases);

            // Subscribe to state changes in recovery steps
            List<Step> steps = plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step instanceof DefaultRecoveryStep)
                    .collect(Collectors.toList());
            steps.forEach(step -> ((DefaultRecoveryStep) step).subscribe(this));

            setPlan(plan);
        }
    }

    private Plan createPlan(List<PodInstanceRequirement> defaultRequirements, List<Phase> phases) {
        phases.addAll(createPhases(defaultRequirements));
        return updatePhases(phases);
    }

    List<Phase> createPhases(Collection<PodInstanceRequirement> podInstanceRequirements) {
        return podInstanceRequirements.stream()
                .map(podInstanceRequirement -> createStep(podInstanceRequirement))
                .map(step -> new DefaultPhase(
                        step.getName(),
                        Arrays.asList(step),
                        new ParallelStrategy<Step>(),
                        Collections.emptyList()))
                .collect(Collectors.toList());
    }


    private Plan updatePhases(List<Phase> overridePhases) {
        Map<String, Phase> phaseMap = new HashMap<>();
        getPlan().getChildren().forEach(phase -> phaseMap.put(phase.getName(), phase));
        overridePhases.forEach(phase -> phaseMap.put(phase.getName(), phase));
        List<Phase> phases = new ArrayList<>(phaseMap.values());

        return DeployPlanFactory.getPlan(DEFAULT_RECOVERY_PLAN_NAME, phases, new ParallelStrategy<>());
    }

    private boolean isTaskPermanentlyFailed(Protos.TaskInfo taskInfo) {
        return FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo);
    }

    private List<PodInstanceRequirement> getRecoveryRequirements(Collection<PodInstanceRequirement> dirtyAssets)
            throws TaskException {

        Collection<Protos.TaskInfo> failedTasks = StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
        logger.info("Found tasks needing recovery: " + getTaskNames(failedTasks));

        List<PodInstanceRequirement> failedPods = TaskUtils.getPodRequirements(
                configStore,
                failedTasks,
                stateStore.fetchTasks());
        failedPods = failedPods.stream()
                .filter(pod -> !PlanUtils.assetConflicts(pod, dirtyAssets))
                .collect(Collectors.toList());
        logger.info("Found pods needing recovery: " + getPodNames(failedPods));

        List<PodInstanceRequirement> inProgressRecoveries = getPlan().getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> !step.isComplete())
                .map(step -> step.getPodInstanceRequirement())
                .filter(requirement -> requirement.isPresent())
                .map(requirement -> requirement.get())
                .collect(Collectors.toList());
        logger.info("Found recoveries already in progress: " + getPodNames(inProgressRecoveries));

        failedPods = failedPods.stream()
                .filter(pod -> !PlanUtils.assetConflicts(pod, inProgressRecoveries))
                .collect(Collectors.toList());
        logger.info("New pods needing recovery: " + getPodNames(failedPods));

        List<PodInstanceRequirement> recoveryRequirements = new ArrayList<>();
        for (PodInstanceRequirement failedPod : failedPods) {
            List<Protos.TaskInfo> failedPodTaskInfos = failedPod.getTasksToLaunch().stream()
                    .map(taskSpecName -> TaskSpec.getInstanceName(failedPod.getPodInstance(), taskSpecName))
                    .map(taskInfoName -> stateStore.fetchTask(taskInfoName))
                    .filter(taskInfo -> taskInfo.isPresent())
                    .map(taskInfo -> taskInfo.get())
                    .collect(Collectors.toList());

            logFailedPod(failedPod.getPodInstance().getName(), failedPodTaskInfos);

            // Pods are atomic, even when considering their status as having either permanently or transiently failed.
            // In order for a Pod to be considered permanently failed, all its constituent tasks must have permanently
            // failed.  Otherwise, we will continue to recover from task failures, in place.
            PodInstanceRequirement podInstanceRequirement = null;
            if (failedPodTaskInfos.stream().allMatch(taskInfo -> isTaskPermanentlyFailed(taskInfo))) {
                logger.info("Recovering permanently failed pod: '{}'", failedPod);
                podInstanceRequirement = PodInstanceRequirement.newBuilder(failedPod)
                        .recoveryType(RecoveryType.PERMANENT)
                        .build();
            } else if (failedPodTaskInfos.stream().noneMatch(taskInfo -> isTaskPermanentlyFailed(taskInfo))) {
                logger.info("Recovering transiently failed pod: '{}'", failedPod);
                podInstanceRequirement = PodInstanceRequirement.newBuilder(failedPod)
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();
            } else {
                logger.error(
                        "Tasks within pod: {} have failed in transient and permanent states and cannot be processed.",
                        failedPod.getName());
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

    private void logFailedPod(String failedPodName, List<Protos.TaskInfo> failedTasks) {
        List<String> permanentlyFailedTasks = failedTasks.stream()
                .filter(taskInfo -> isTaskPermanentlyFailed(taskInfo))
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toList());

        List<String> transientlyFailedTasks = failedTasks.stream()
                .filter(taskInfo -> !isTaskPermanentlyFailed(taskInfo))
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toList());

        logger.info("Failed tasks in pod: {}, permanent[{}], transient[{}]",
                failedPodName,
                permanentlyFailedTasks,
                transientlyFailedTasks);
    }

    Step createStep(PodInstanceRequirement podInstanceRequirement) {
        logger.info("Creating step: {}", podInstanceRequirement);
        return new DefaultRecoveryStep(
                podInstanceRequirement.getName(),
                Status.PENDING,
                podInstanceRequirement,
                launchConstrainer,
                stateStore);
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

    @Override
    public void update(Observable obj) {
        if (obj instanceof DefaultRecoveryStep) {

            /**
             * Any step which has completed work on a pod is no longer permanently failed.  A pod may have been marked
             * as permanently failed either by human intervention or by a FailureMonitor determining a pod has met its
             * failure criteria.  See the {@link DefaultTaskFailureListener} as an example of tasks being marked
             * permanently failed.  It should remain marked as permanently failed until its recovery is complete so that
             * resources reserved in partial recovery are freed.
             */
            DefaultRecoveryStep step = (DefaultRecoveryStep) obj;
            if (step.isComplete() && step.getPodInstanceRequirement().isPresent()) {
                PodInstance podInstance = step.getPodInstanceRequirement().get().getPodInstance();
                stateStore.storeTasks(FailureUtils.clearFailed(podInstance, stateStore));
            }
        }

        notifyObservers();
    }

    private List<String> getTaskNames(Collection<Protos.TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toList());
    }

    private List<String> getPodNames(Collection<PodInstanceRequirement> podInstanceRequirements) {
        return podInstanceRequirements.stream()
                .map(podInstanceRequirement -> podInstanceRequirement.getPodInstance())
                .map(podInstance -> podInstance.getName())
                .collect(Collectors.toList());
    }

}
