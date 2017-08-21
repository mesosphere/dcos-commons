package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager implements PlanManager {
    public static final String DEFAULT_RECOVERY_PLAN_NAME = "recovery";
    public static final String DEFAULT_RECOVERY_PHASE_NAME = "default";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ConfigStore<ServiceSpec> configStore;
    private final List<RecoveryPlanOverrider> recoveryPlanOverriders;
    private final Set<String> recoverableTaskNames;

    protected volatile Plan plan;

    protected final StateStore stateStore;
    protected final FailureMonitor failureMonitor;
    protected final LaunchConstrainer launchConstrainer;
    protected final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Set<String> recoverableTaskNames,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this(stateStore, configStore, recoverableTaskNames, launchConstrainer, failureMonitor, Collections.emptyList());
    }

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Set<String> recoverableTaskNames,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            List<RecoveryPlanOverrider> overrideRecoveryManagers) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.recoverableTaskNames = recoverableTaskNames;
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

            setPlan(createPlan(defaultRequirements, phases));
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
        return FailureUtils.isPermanentlyFailed(taskInfo) || failureMonitor.hasFailed(taskInfo);
    }

    private List<PodInstanceRequirement> getRecoveryRequirements(Collection<PodInstanceRequirement> dirtyAssets)
            throws TaskException {

        Collection<Protos.TaskInfo> failedTasks = StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
        failedTasks = failedTasks.stream()
                .filter(taskInfo -> recoverableTaskNames.contains(taskInfo.getName()))
                .collect(Collectors.toList());
        logger.info("Found tasks needing recovery: {}", getTaskNames(failedTasks));

        List<PodInstanceRequirement> failedPods = TaskUtils.getPodRequirements(
                configStore,
                failedTasks,
                stateStore.fetchTasks());
        if (!failedPods.isEmpty()) {
            logger.info("All failed pods: {}", getPodNames(failedPods));
        }
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
                podInstanceRequirement = getPermanentlyFailedPodRequirement(failedPod);
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

    private PodInstanceRequirement getPermanentlyFailedPodRequirement(PodInstanceRequirement failedPod)
            throws TaskException {

        // Permanently failed pods should be recovered using the latest target configuration
        ServiceSpec serviceSpec;
        try {
            serviceSpec = configStore.fetch(configStore.getTargetConfig());
        } catch (ConfigStoreException e) {
            throw new TaskException(e);
        }

        // PodSpecs are already guaranteed to be of a unique type so finding the first one is fine.
        Optional<PodSpec> podSpec = serviceSpec.getPods().stream()
                .filter(ps -> ps.getType().equals(failedPod.getPodInstance().getPod().getType()))
                .findFirst();

        if (!podSpec.isPresent()) {
            throw new TaskException(
                    String.format(
                            "Permanently failed pod of type '%s' is not present in the target configuration.",
                            failedPod.getPodInstance().getPod().getType()));

        }

        PodInstance podInstance = new DefaultPodInstance(podSpec.get(), failedPod.getPodInstance().getIndex());
        return PodInstanceRequirement.newBuilder(podInstance, failedPod.getTasksToLaunch())
                        .recoveryType(RecoveryType.PERMANENT)
                        .build();
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

    private List<String> getTaskNames(Collection<Protos.TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toList());
    }

    private static List<String> getPodNames(Collection<PodInstanceRequirement> podInstanceRequirements) {
        return podInstanceRequirements.stream()
                .map(podInstanceRequirement -> podInstanceRequirement.getPodInstance())
                .map(podInstance -> podInstance.getName())
                .collect(Collectors.toList());
    }

}
