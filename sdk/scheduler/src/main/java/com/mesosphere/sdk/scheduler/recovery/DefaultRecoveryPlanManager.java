package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link RecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager implements PlanManager {
    public static final String DEFAULT_RECOVERY_PLAN_NAME = "recovery";
    public static final String DEFAULT_RECOVERY_PHASE_NAME = "default";

    protected final Logger logger;
    protected final ConfigStore<ServiceSpec> configStore;
    private final List<RecoveryPlanOverrider> recoveryPlanOverriders;
    private final Set<String> recoverableTaskNames;

    protected volatile Plan plan;

    protected final StateStore stateStore;
    protected final FailureMonitor failureMonitor;
    protected final LaunchConstrainer launchConstrainer;
    protected final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            String serviceName,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Set<String> recoverableTaskNames,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this(serviceName,
                stateStore,
                configStore,
                recoverableTaskNames,
                launchConstrainer,
                failureMonitor,
                Collections.emptyList());
    }

    public DefaultRecoveryPlanManager(
            String serviceName,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Set<String> recoverableTaskNames,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            List<RecoveryPlanOverrider> overrideRecoveryManagers) {
        this.logger = LoggingUtils.getLogger(getClass(), serviceName);
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

    @Override
    public void setPlan(Plan plan) {
        throw new UnsupportedOperationException("Setting plans on the RecoveryPlanManager is not allowed.");
    }

    protected void setPlanInternal(Plan plan) {
        synchronized (planLock) {
            this.plan = plan;
            List<String> stepNames = plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .map(step -> step.getName())
                    .collect(Collectors.toList());
            try {
                logger.info("Recovery plan set to: {}", SerializationUtils.toJsonString(PlanInfo.forPlan(plan)));
            } catch (IOException e) {
                logger.error("Failed to serialize plan to JSON. Recovery plan set to: {}", stepNames);
            }
        }
    }

    @Override
    public Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets) {
        synchronized (planLock) {
            updatePlan(dirtyAssets);
            return getPlan().getCandidates(dirtyAssets).stream()
                    .filter(step ->
                            launchConstrainer.canLaunch(((RecoveryStep) step).getRecoveryType()))
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
            Collection<PodInstanceRequirement> newRequirements = null;

            try {
                newRequirements = getNewRecoveryRequirements(dirtyAssets);
            } catch (TaskException e) {
                logger.error("Failed to generate steps.", e);
                return;
            }

            List<PodInstanceRequirement> defaultRequirements = new ArrayList<>();
            List<Phase> phases = new ArrayList<>();
            for (PodInstanceRequirement requirement : newRequirements) {
                boolean overridden = false;
                for (RecoveryPlanOverrider overrider : recoveryPlanOverriders) {
                    Optional<Phase> override  = overrider.override(requirement);
                    if (override.isPresent()) {
                        overridden = true;
                        phases.add(override.get());
                    }
                }

                if (!overridden) {
                    defaultRequirements.add(requirement);
                }
            }

            setPlanInternal(createPlan(defaultRequirements, phases));
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

    private static boolean phaseOverriden(Phase phase, Collection<PodInstanceRequirement> newReqs) {
        // If at least 1 new requirement conflicts with the phase's steps, the phase has been overridden
        return phase.getChildren().stream()
                .map(step -> step.getPodInstanceRequirement())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(req -> PlanUtils.assetConflicts(req, newReqs))
                .count() > 0;
    }

    private Plan updatePhases(List<Phase> overridePhases) {
        Collection<PodInstanceRequirement> newReqs = overridePhases.stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(step -> step.getPodInstanceRequirement())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        Collection<Phase> inProgressPhases = getPlan().getChildren().stream()
                .filter(phase -> !phaseOverriden(phase, newReqs))
                .collect(Collectors.toList());

        List<Phase> phases = new ArrayList<>();
        phases.addAll(inProgressPhases);
        phases.addAll(overridePhases);

        return DeployPlanFactory.getPlan(DEFAULT_RECOVERY_PLAN_NAME, phases, new ParallelStrategy<>());
    }

    private boolean isTaskPermanentlyFailed(Protos.TaskInfo taskInfo) {
        return FailureUtils.isPermanentlyFailed(taskInfo) || failureMonitor.hasFailed(taskInfo);
    }

    private boolean failureStateHasChanged(PodInstanceRequirement podInstanceRequirement) {
        RecoveryType original = podInstanceRequirement.getRecoveryType();

        Collection<String> taskInfoNames =
                TaskUtils.getTaskNames(
                        podInstanceRequirement.getPodInstance(),
                        podInstanceRequirement.getTasksToLaunch());
        Collection<Protos.TaskInfo> taskInfos =
                StateStoreUtils.fetchPodTasks(stateStore, podInstanceRequirement.getPodInstance()).stream()
                .filter(taskInfo -> taskInfoNames.contains(taskInfo.getName()))
                .collect(Collectors.toList());

        RecoveryType current = getRecoveryType(taskInfos);

        boolean recoveryStateHasChanged = !original.equals(current);
        if (recoveryStateHasChanged) {
            logger.info("Pod: {} recovery state has changed from: {} to: {}",
                    getPodNames(Arrays.asList(podInstanceRequirement)),
                    original,
                    current);
        }

        return recoveryStateHasChanged;
    }

    /**
     * A set of Tasks must have a uniform recovery type.  Either they have all failed permanently or they have all
     * failed transiently.  If this constraint is violated then the Recovery type is {@link RecoveryType#NONE}.
     */
    private RecoveryType getRecoveryType(Collection<Protos.TaskInfo> taskInfos) {
        // PodInstanceRequirements must have a uniform recovery type (PERMANENT OR TRANSIENT).
        if (taskInfos.stream().allMatch(taskInfo -> isTaskPermanentlyFailed(taskInfo))) {
            return RecoveryType.PERMANENT;
        } else if (taskInfos.stream().noneMatch(taskInfo -> isTaskPermanentlyFailed(taskInfo))) {
            return RecoveryType.TRANSIENT;
        } else {
            for (Protos.TaskInfo taskInfo : taskInfos) {
                RecoveryType recoveryType =
                        isTaskPermanentlyFailed(taskInfo) ? RecoveryType.PERMANENT : RecoveryType.TRANSIENT;
                logger.info("Task: {} has recovery type: {}", taskInfo.getName(), recoveryType);
            }
            return RecoveryType.NONE;
        }
    }

    private List<PodInstanceRequirement> getNewRecoveryRequirements(Collection<PodInstanceRequirement> dirtyAssets)
            throws TaskException {

        Collection<Protos.TaskInfo> failedTasks = StateStoreUtils.fetchTasksNeedingRecovery(
                stateStore,
                configStore,
                recoverableTaskNames);
        logger.info("Found tasks needing recovery: {}", getTaskNames(failedTasks));

        List<Protos.TaskInfo> allLaunchedTasks = stateStore.fetchTasks().stream()
                .filter(taskInfo -> stateStore.fetchStatus(taskInfo.getName()).isPresent())
                .collect(Collectors.toList());

        List<PodInstanceRequirement> failedPods = TaskUtils.getPodRequirements(
                configStore,
                failedTasks,
                allLaunchedTasks);
        if (!failedPods.isEmpty()) {
            logger.info("All failed tasks: {}", getPodNames(failedPods));
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
                .filter(requirement -> !failureStateHasChanged(requirement))
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

            RecoveryType recoveryType = getRecoveryType(failedPodTaskInfos);
            if (RecoveryType.NONE.equals(recoveryType)) {
                logger.error(
                        "Cannot recover tasks within pod: '{}' due to having recovery type: '{}'.",
                        failedPod.getName(),
                        recoveryType.name());
                continue;
            }

            logger.info("Recovering {} failed pod: '{}'", recoveryType.name(), failedPod);
            PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(failedPod)
                    .recoveryType(recoveryType)
                    .build();

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

        logger.info("Failed tasks in pod: {}, permanent{}, transient{}",
                failedPodName, permanentlyFailedTasks, transientlyFailedTasks);
    }

    Step createStep(PodInstanceRequirement podInstanceRequirement) {
        logger.info("Creating step: {}", podInstanceRequirement);
        return new RecoveryStep(
                podInstanceRequirement.getName(),
                podInstanceRequirement,
                launchConstrainer,
                stateStore);
    }

    @Override
    public Set<PodInstanceRequirement> getDirtyAssets() {
        return PlanUtils.getDirtyAssets(plan);
    }

    private List<String> getTaskNames(Collection<Protos.TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toList());
    }

    private static List<String> getPodNames(Collection<PodInstanceRequirement> podInstanceRequirements) {
        return podInstanceRequirements.stream()
                .map(podInstanceRequirement -> String.format("%s:%s",
                        podInstanceRequirement.getPodInstance().getName(), podInstanceRequirement.getTasksToLaunch()))
                .collect(Collectors.toList());
    }

}
