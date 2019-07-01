package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DeployPlanFactory;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.backoff.ExponentialBackOff;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link RecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
@SuppressWarnings({
    "checkstyle:LineLength",
    "checkstyle:DeclarationOrder",
    "checkstyle:HiddenField",
})
public class DefaultRecoveryPlanManager implements PlanManager {
  public static final String DEFAULT_RECOVERY_PHASE_NAME = "default";

  protected final Logger logger;

  protected final ConfigStore<ServiceSpec> configStore;

  private final Optional<String> namespace;

  private final List<RecoveryPlanOverrider> recoveryPlanOverriders;

  private final Set<String> recoverableTaskNames;

  protected volatile Plan plan;

  protected final StateStore stateStore;

  protected final FailureMonitor failureMonitor;

  protected final LaunchConstrainer launchConstrainer;

  final ExponentialBackOff exponentialBackOff;

  protected final Object planLock = new Object();

  public DefaultRecoveryPlanManager(
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      Set<String> recoverableTaskNames,
      LaunchConstrainer launchConstrainer,
      FailureMonitor failureMonitor,
      Optional<String> namespace)
  {
    this(
        stateStore,
        configStore,
        recoverableTaskNames,
        launchConstrainer,
        failureMonitor,
        namespace,
        Collections.emptyList(), ExponentialBackOff.getInstance());
  }

  public DefaultRecoveryPlanManager(
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      Set<String> recoverableTaskNames,
      LaunchConstrainer launchConstrainer,
      FailureMonitor failureMonitor,
      Optional<String> namespace,
      List<RecoveryPlanOverrider> recoveryPlanOverriders,
      ExponentialBackOff exponentialBackOff)
  {
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.stateStore = stateStore;
    this.configStore = configStore;
    this.recoverableTaskNames = recoverableTaskNames;
    this.failureMonitor = failureMonitor;
    this.launchConstrainer = launchConstrainer;
    this.namespace = namespace;
    this.recoveryPlanOverriders = recoveryPlanOverriders;
    //TODO@kjoshi: Recovery plans ARE DefaultPlans, just with a different name
    plan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME, Collections.emptyList());
    this.exponentialBackOff = exponentialBackOff;
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

  private void setPlanInternal(Plan plan) {
    synchronized (planLock) {
      this.plan = plan;
      // Avoid logging for non-empty completed plan, as is the case when a previous recovery had occurred:
      if (!plan.getChildren().isEmpty() && !plan.isComplete()) {
        try {
          logger.info("Recovery plan set to: {}",
              SerializationUtils.toShortJsonString(PlanInfo.forPlan(plan)));
        } catch (IOException e) {
          List<String> stepNames = plan.getChildren().stream()
              .flatMap(phase -> phase.getChildren().stream())
              .map(step -> step.getName())
              .collect(Collectors.toList());
          logger.error("Failed to serialize plan to JSON. Recovery plan set to: {}", stepNames);
        }
      }
    }
  }

  @Override
  public Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets) {
    synchronized (planLock) {
      logger.info("getCandidates method called 8");
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

  private void updatePlan(Collection<PodInstanceRequirement> dirtyAssets) {
    if (!dirtyAssets.isEmpty()) {
      logger.info("Dirty assets for recovery plan consideration: {}", dirtyAssets);
    }

    synchronized (planLock) {
      Collection<PodInstanceRequirement> newRequirements;
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
          Optional<Phase> override = overrider.override(requirement);
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

  private List<Phase> createPhases(Collection<PodInstanceRequirement> podInstanceRequirements) {
    return podInstanceRequirements.stream()
        .map(this::createStep)
        .map(step -> new DefaultPhase(
            step.getName(),
            Collections.singletonList(step),
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

    //TODO@kjoshi: This is where an actual RECOVERY plan is created with its constituent Phases.
    return DeployPlanFactory.getPlan(Constants.RECOVERY_PLAN_NAME, phases, new ParallelStrategy<>());
  }

  private boolean isTaskPermanentlyFailed(Protos.TaskInfo taskInfo) {
    return FailureUtils.isPermanentlyFailed(taskInfo) || failureMonitor.hasFailed(taskInfo);
  }

  private List<PodInstanceRequirement> getNewRecoveryRequirements(Collection<PodInstanceRequirement> dirtyAssets)
      throws TaskException
  {

    List<PodInstanceRequirement> newFailedPods = getNewFailedPods(dirtyAssets);
    List<PodInstanceRequirement> recoveryRequirements = new ArrayList<>();

    for (PodInstanceRequirement failedPod : newFailedPods) {
      List<Protos.TaskInfo> failedPodTaskInfos = failedPod.getTasksToLaunch().stream()
          .map(taskSpecName -> CommonIdUtils.getTaskInstanceName(failedPod.getPodInstance(), taskSpecName))
          .map(stateStore::fetchTask)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());

      logFailedPod(failedPod.getPodInstance().getName(), failedPodTaskInfos);

      RecoveryType recoveryType = getTaskRecoveryType(failedPodTaskInfos);
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

  private List<PodInstanceRequirement> getNewFailedPods(Collection<PodInstanceRequirement> dirtyAssets)
      throws TaskException
  {
    Collection<Protos.TaskInfo> allTaskInfos = stateStore.fetchTasks();
    Collection<Protos.TaskStatus> allTaskStatuses = stateStore.fetchStatuses();

    FailureUtils.setPermanentlyFailed(
        stateStore,
        TaskUtils.getTasksForReplacement(allTaskStatuses, allTaskInfos));

    Collection<Protos.TaskInfo> failedTasks =
        TaskUtils.getTasksNeedingRecovery(configStore, allTaskInfos, allTaskStatuses).stream()
            .filter(taskInfo -> recoverableTaskNames.contains(taskInfo.getName()))
            .collect(Collectors.toList());
    if (!failedTasks.isEmpty()) {
      logger.info("Tasks needing recovery: {}", getTaskNames(failedTasks));
    }

    List<PodInstanceRequirement> failedPods =
        TaskUtils.getPodRequirements(configStore, allTaskInfos, allTaskStatuses, failedTasks, exponentialBackOff);
    if (!failedPods.isEmpty()) {
      logger.info("All failed pods: {}", getPodNames(failedPods));
    }

    failedPods = failedPods.stream()
        .filter(pod -> !PlanUtils.assetConflicts(pod, dirtyAssets))
        .collect(Collectors.toList());
    if (!failedPods.isEmpty()) {
      logger.info("Pods needing recovery: {}", getPodNames(failedPods));
    }

    //TODO@kjoshi: PENDING_DELAYED will cause !step.isComplete() here, ensure PENDING_DELAYED doesn't get added to the plan.
    List<PodInstanceRequirement> incompleteRecoveries = getPlan().getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> !step.isComplete() && step.getPodInstanceRequirement().isPresent())
        .map(step -> step.getPodInstanceRequirement().get())
        .collect(Collectors.toList());
    if (!incompleteRecoveries.isEmpty()) {
      logger.info("Pods with incomplete recoveries: {}", getPodNames(incompleteRecoveries));
    }

    // Build map of Steps (PodInstanceRequirements) to pod instances
    // e.g. journal-0 --> [bootstrap, node]

    // Initialize map with empty arrays for values
    Map<PodInstance, List<PodInstanceRequirement>> recoveryMap = new HashMap<>();
    incompleteRecoveries.forEach(
        podInstanceRequirement -> recoveryMap.put(podInstanceRequirement.getPodInstance(), new ArrayList<>()));

    for (PodInstanceRequirement inProgressRecovery : incompleteRecoveries) {
      recoveryMap.get(inProgressRecovery.getPodInstance()).add(inProgressRecovery);
    }

    Collection<PodInstanceRequirement> inProgressRecoveries = new ArrayList<>();
    for (Map.Entry<PodInstance, List<PodInstanceRequirement>> entry : recoveryMap.entrySet()) {
      // If a pod's failure state has escalated (transient --> permanent) it should NOT be counted as
      // in progress, so that it can be re-evaluated for the appropriate recovery approach.
      if (!failureStateHasEscalated(allTaskInfos, entry.getKey(), entry.getValue())) {
        inProgressRecoveries.addAll(entry.getValue());
      }
    }
    if (!inProgressRecoveries.isEmpty()) {
      logger.info("Pods with recoveries already in progress: {}", getPodNames(inProgressRecoveries));
    }

    failedPods = failedPods.stream()
        .filter(pod -> !PlanUtils.assetConflicts(pod, inProgressRecoveries))
        .collect(Collectors.toList());
    if (!failedPods.isEmpty()) {
      logger.info("New pods needing recovery: {}", getPodNames(failedPods));
    }

    return failedPods;
  }

  private void logFailedPod(String failedPodName, List<Protos.TaskInfo> failedTasks) {
    List<String> permanentlyFailedTasks = failedTasks.stream()
        .filter(this::isTaskPermanentlyFailed)
        .map(Protos.TaskInfo::getName)
        .collect(Collectors.toList());

    List<String> transientlyFailedTasks = failedTasks.stream()
        .filter(taskInfo -> !isTaskPermanentlyFailed(taskInfo))
        .map(taskInfo -> taskInfo.getName())
        .collect(Collectors.toList());

    if (!permanentlyFailedTasks.isEmpty() || !transientlyFailedTasks.isEmpty()) {
      logger.info("Failed tasks in pod: {}, permanent{}, transient{}",
          failedPodName, permanentlyFailedTasks, transientlyFailedTasks);
    }
  }

  /**
   * A pod instance failure has escalated if it has transitioned from TRANSIENT to PERMANENT ({@link RecoveryType}.
   */
  private boolean failureStateHasEscalated(
      Collection<Protos.TaskInfo> allTaskInfos,
      PodInstance podInstance,
      Collection<PodInstanceRequirement> podInstanceRequirements)
  {

    RecoveryType original = getPodRecoveryType(podInstanceRequirements);
    RecoveryType current = getTaskRecoveryType(TaskUtils.getPodTasks(podInstance, allTaskInfos));

    boolean failureStateHasEscalated = false;
    if (original.equals(RecoveryType.TRANSIENT) && current.equals(RecoveryType.PERMANENT)) {
      failureStateHasEscalated = true;
    }

    String mode = failureStateHasEscalated ? "" : "NOT ";
    List<String> podNames = getPodNames(podInstanceRequirements);
    logger.info("Failure state for {} has {}escalated. original: {}, current: {}",
        podNames, mode, original, current);

    return failureStateHasEscalated;
  }

  private static RecoveryType getPodRecoveryType(Collection<PodInstanceRequirement> podInstanceRequirements) {
    if (podInstanceRequirements.stream()
        .anyMatch(req -> req.getRecoveryType().equals(RecoveryType.PERMANENT)))
    {
      return RecoveryType.PERMANENT;
    } else {
      return RecoveryType.TRANSIENT;
    }
  }

  private RecoveryType getTaskRecoveryType(Collection<Protos.TaskInfo> taskInfos) {
    if (taskInfos.stream().allMatch(this::isTaskPermanentlyFailed)) {
      return RecoveryType.PERMANENT;
    } else if (taskInfos.stream().noneMatch(this::isTaskPermanentlyFailed)) {
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

  private Step createStep(PodInstanceRequirement podInstanceRequirement) {
    logger.info("Creating step: {}", podInstanceRequirement);
    return new RecoveryStep(
        podInstanceRequirement.getName(),
        podInstanceRequirement,
        launchConstrainer,
        stateStore,
        namespace);
  }

  @Override
  public Set<PodInstanceRequirement> getDirtyAssets() {
    return PlanUtils.getDirtyAssets(plan);
  }

  private static List<String> getTaskNames(Collection<Protos.TaskInfo> taskInfos) {
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
