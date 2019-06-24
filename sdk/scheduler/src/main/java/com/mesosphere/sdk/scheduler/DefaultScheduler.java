package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.debug.OfferOutcomeTrackerV2;
import com.mesosphere.sdk.debug.PlansTracker;
import com.mesosphere.sdk.debug.TaskStatusesTracker;
import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.http.endpoints.ArtifactResource;
import com.mesosphere.sdk.http.endpoints.ConfigResource;
import com.mesosphere.sdk.http.endpoints.DebugOffersResource;
import com.mesosphere.sdk.http.endpoints.DebugResource;
import com.mesosphere.sdk.http.endpoints.DeprecatedPlanResource;
import com.mesosphere.sdk.http.endpoints.EndpointsResource;
import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.PlansDebugResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.http.endpoints.PodResource;
import com.mesosphere.sdk.http.endpoints.StateResource;
import com.mesosphere.sdk.http.endpoints.TaskStatusesResource;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.scheduler.plan.DecommissionPlanManager;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanCustomizer;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.PlanScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.uninstall.UninstallRecorder;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This scheduler when provided with a ServiceSpec will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpec will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
@SuppressWarnings({
    "checkstyle:MultipleStringLiterals",
    "checkstyle:IllegalCatch",
    "checkstyle:LineLength",
    "checkstyle:ParameterNumber",
    "checkstyle:DeclarationOrder"
})
public class DefaultScheduler extends AbstractScheduler {

  private final Logger logger;

  private final Optional<String> namespace;

  private final FrameworkStore frameworkStore;

  private final ConfigStore<ServiceSpec> configStore;

  private final GoalState goalState;

  private final PlanManager deploymentPlanManager;

  private boolean deploymentCompletionWasStored;

  private final PlanManager recoveryPlanManager;

  private final Collection<Object> customResources;

  private final Map<String, EndpointProducer> customEndpointProducers;

  private final PersistentLaunchRecorder launchRecorder;

  private final Optional<UninstallRecorder> decommissionRecorder;

  private final Optional<OfferOutcomeTracker> offerOutcomeTracker;

  private final Optional<OfferOutcomeTrackerV2> offerOutcomeTrackerV2;

  private final Optional<PlansTracker> plansTracker;

  private final Optional<TaskStatusesTracker> statusesTracker;

  private final PlanScheduler planScheduler;

  /**
   * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service,
   * including details such as the service name, the pods/tasks to be deployed, and the plans describing how the
   * deployment should be organized.
   */
  public static SchedulerBuilder newBuilder(
      ServiceSpec serviceSpec,
      SchedulerConfig schedulerConfig) throws PersisterException
  {
    return new SchedulerBuilder(serviceSpec, schedulerConfig);
  }

  /**
   * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service,
   * including details such as the service name, the pods/tasks to be deployed, and the plans describing how the
   * deployment should be organized.
   */
  public static SchedulerBuilder newBuilder(
      ServiceSpec serviceSpec,
      SchedulerConfig schedulerConfig,
      Persister persister) throws PersisterException
  {
    return new SchedulerBuilder(serviceSpec, schedulerConfig, persister);
  }

  /**
   * Creates a new DefaultScheduler. See information about parameters in {@link SchedulerBuilder}.
   */
  protected DefaultScheduler(
      ServiceSpec serviceSpec,
      SchedulerConfig schedulerConfig,
      Optional<String> namespace,
      Collection<Object> customResources,
      PlanCoordinator planCoordinator,
      Optional<PlanCustomizer> planCustomizer,
      FrameworkStore frameworkStore,
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      ArtifactQueries.TemplateUrlFactory templateUrlFactory,
      Map<String, EndpointProducer> customEndpointProducers) throws ConfigStoreException
  {
    super(serviceSpec, schedulerConfig, stateStore, planCoordinator, planCustomizer, namespace);
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.namespace = namespace;
    this.frameworkStore = frameworkStore;
    this.configStore = configStore;
    this.goalState = serviceSpec.getGoal();
    this.customResources = customResources;
    this.customEndpointProducers = customEndpointProducers;

    this.launchRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec, namespace);
    Optional<DecommissionPlanManager> decommissionManager = getDecommissionManager(planCoordinator);
    if (decommissionManager.isPresent()) {
      this.decommissionRecorder =

          Optional.of(new UninstallRecorder(stateStore, decommissionManager.get().getResourceSteps()));
    } else {
      this.decommissionRecorder = Optional.empty();
    }

    // Get the recovery and deploy plan managers. We store the PlanManagers, not the underlying Plans, because
    // PlanManagers can change their plans at any time.
    this.deploymentPlanManager = planCoordinator.getPlanManagers().stream()
        .filter(pm -> pm.getPlan().isDeployPlan())
        .findAny()
        .get();
    this.deploymentCompletionWasStored = false;
    this.recoveryPlanManager = planCoordinator.getPlanManagers().stream()
        .filter(pm -> pm.getPlan().isRecoveryPlan())
        .findAny()
        .get();

    // If the service is namespaced (i.e. part of a multi-service scheduler), disable the OfferOutcomeTracker to
    // reduce memory consumption.
    this.offerOutcomeTracker = namespace.isPresent() ? Optional.empty() : Optional.of(new OfferOutcomeTracker());
    this.offerOutcomeTrackerV2 = namespace.isPresent() ? Optional.empty() : Optional.of(new OfferOutcomeTrackerV2());
    this.statusesTracker = Optional.of(new TaskStatusesTracker(getPlanCoordinator(), stateStore));

    this.planScheduler = new PlanScheduler(
        new OfferEvaluator(
            frameworkStore,
            stateStore,
            offerOutcomeTracker,
            offerOutcomeTrackerV2,
            serviceSpec.getName(),
            configStore.getTargetConfig(),
            templateUrlFactory,
            schedulerConfig,
            namespace),
        stateStore,
        namespace);

    customizePlans();
    this.plansTracker = Optional.of(new PlansTracker(getPlanCoordinator(), stateStore));
  }

  @Override
  public Collection<Object> getHTTPEndpoints() {
    Collection<Object> resources = new ArrayList<>();
    resources.addAll(customResources);
    resources.add(new ArtifactResource(configStore));
    resources.add(new ConfigResource(configStore));
    EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName(), schedulerConfig);
    for (Map.Entry<String, EndpointProducer> entry : customEndpointProducers.entrySet()) {
      endpointsResource.setCustomEndpoint(entry.getKey(), entry.getValue());
    }
    resources.add(endpointsResource);
    PlansResource plansResource = new PlansResource(planCoordinator);
    resources.add(plansResource);
    resources.add(new DeprecatedPlanResource(plansResource));
    resources.add(new HealthResource(planCoordinator, Optional.of(frameworkStore)));
    resources.add(new PodResource(stateStore, configStore, serviceSpec.getName()));
    resources.add(new StateResource(frameworkStore, stateStore, new StringPropertyDeserializer()));
    offerOutcomeTrackerV2.ifPresent(x -> resources.add(new DebugOffersResource(x)));
    offerOutcomeTracker.ifPresent(x -> resources.add(new DebugResource(x)));
    plansTracker.ifPresent(x -> resources.add(new PlansDebugResource(x)));
    statusesTracker.ifPresent(x -> resources.add(new TaskStatusesResource(x)));
    return resources;
  }

  @Override
  public Map<String, EndpointProducer> getCustomEndpoints() {
    return customEndpointProducers;
  }

  @Override
  public ConfigStore<ServiceSpec> getConfigStore() {
    return configStore;
  }

  @Override
  protected void registeredWithMesos() {
    Set<String> activeTasks = PlanUtils.getLaunchableTasks(getPlans());

    Optional<DecommissionPlanManager> decommissionManager = getDecommissionManager(getPlanCoordinator());
    if (decommissionManager.isPresent()) {
      Collection<String> decommissionedTasks = decommissionManager.get().getTasksToDecommission().stream()
          .map(taskInfo -> taskInfo.getName())
          .collect(Collectors.toList());
      activeTasks.addAll(decommissionedTasks);
    }

    if (schedulerConfig.useLegacyUnneededTaskKills()) {
      //TODO(kjoshi): Remove this function on or after Dec 2018.
      //This case is turned off by default.
      legacyKillUnneededTasks(stateStore, activeTasks, logger);
    } else {
      killUnneededTasks(stateStore, activeTasks);
    }
  }

  @Override
  public void unregistered() {
    throw new UnsupportedOperationException(
        "Should not have received unregistered call. This is only applicable to UninstallSchedulers");
  }

  @Override
  protected ClientStatusResponse getStatus() {
    // Take this opportunity to store whether we've completed our deploy plan.
    // This ensures that we correctly select the custom update plan if one is provided during an update.
    boolean deployCompleted = deploymentPlanManager.getPlan().isComplete();
    if (deployCompleted && !deploymentCompletionWasStored) {
      logger.info("Marking deployment as completed");
      StateStoreUtils.setDeploymentWasCompleted(stateStore);
      // Avoid hitting StateStore too much...:
      deploymentCompletionWasStored = true;
    }

    if (goalState == GoalState.FINISH && deployCompleted && recoveryPlanManager.getPlan().isComplete()) {
      // Service has a FINISH goal state, and deployment+recovery are complete. Tell upstream to uninstall us.
      return ClientStatusResponse.readyToUninstall();
    } else if (!deployCompleted
        || isReplacing(recoveryPlanManager))
    {
      //TODO@kjoshi: workSetTracker.hasNewWork() 
      // Service is acquiring footprint, either via initial deployment or via replacing a task
      return ClientStatusResponse.footprint(workSetTracker.hasNewWork());
    } else if (getPlanCoordinator().getPlanManagers().stream().anyMatch(pm -> isWorking(pm.getPlan()))) {
      // One or more plans (including e.g. sidecar plans) is doing work: Not idle
      return ClientStatusResponse.launching(workSetTracker.hasNewWork());
    } else {
      // All plans are complete, or are not doing work: Idle
      return ClientStatusResponse.idle();
    }
  }

  /**
   * Returns whether the plan appears to currently be doing work, or is wanting to perform work. If {@code false} then
   * the plan is not currently performing work.
   */
  private static boolean isWorking(Plan plan) {
    switch (plan.getStatus()) {
      case PENDING:
      case IN_PROGRESS:
      case PREPARED:
      case STARTED:
      case STARTING:
        // The plan has work left to do, or is currently actively doing work.
        return true;
      case COMPLETE:
      case ERROR:
      case WAITING:
        // The plan currently has no work to do: finished, or in a stopped state.
        return false;
      default:
        throw new IllegalStateException(
            String.format("Unsupported status in %s plan: %s", plan.getName(), plan.getStatus()));
    }
  }

  private static boolean isReplacing(PlanManager recoveryPlanManager) {
    return !recoveryPlanManager.getPlan().isComplete()
        && recoveryPlanManager.getPlan().getChildren().stream()
        .filter(phase -> !phase.isComplete())
        .flatMap(phase -> phase.getChildren().stream())
        .anyMatch(step ->
            !step.isComplete()
                && step instanceof RecoveryStep
                && ((RecoveryStep) step).getRecoveryType() == RecoveryType.PERMANENT);
  }

  private static Optional<DecommissionPlanManager> getDecommissionManager(PlanCoordinator planCoordinator) {
    return planCoordinator.getPlanManagers().stream()
        .filter(planManager -> planManager.getPlan().isDecommissionPlan())
        .map(planManager -> (DecommissionPlanManager) planManager)
        .findFirst();
  }

  private static void killUnneededTasks(StateStore stateStore, Set<String> taskToDeployNames) {
    Set<Protos.TaskInfo> unneededTaskInfos = stateStore.fetchTasks().stream()
        .filter(taskInfo -> !taskToDeployNames.contains(taskInfo.getName()))
        .collect(Collectors.toSet());

    Set<Protos.TaskID> taskIdsToKill = unneededTaskInfos.stream()
        .map(taskInfo -> taskInfo.getTaskId())
        .collect(Collectors.toSet());

    taskIdsToKill.forEach(taskID -> TaskKiller.killTask(taskID));
  }

  private static void legacyKillUnneededTasks(StateStore stateStore, Set<String> taskToDeployNames, Logger logger) {

    //This method is retained to understand why we're cleaning out TaskInfo's and why
    //a global kill was issued to pending tasks. This method can be invoked in frameworks
    //that do not have require extensive cleanup via decommissioning. The environment
    //variable USE_LEGACY_KILL_UNNEEDED_TASKS should be set to invoke this method at runtime.
    //TODO(kjoshi): Remove this function on or after Dec 2018.

    Set<Protos.TaskInfo> unneededTaskInfos = stateStore.fetchTasks().stream()
        .filter(taskInfo -> !taskToDeployNames.contains(taskInfo.getName()))
        .collect(Collectors.toSet());

    Set<Protos.TaskID> taskIdsToKill = unneededTaskInfos.stream()
        .map(taskInfo -> taskInfo.getTaskId())
        .collect(Collectors.toSet());

    // Clear the TaskIDs from the TaskInfos so we drop all future TaskStatus Messages
    //TODO(kjoshi): Investigate why this is needed anymore.
    Set<Protos.TaskInfo> cleanedTaskInfos = unneededTaskInfos.stream()
        .map(taskInfo -> taskInfo.toBuilder())
        .map(builder -> builder.setTaskId(Protos.TaskID.newBuilder().setValue("")).build())
        .collect(Collectors.toSet());

    // Remove both TaskInfo and TaskStatus, then store the cleaned TaskInfo one at a time to limit damage in the
    // event of an untimely scheduler crash.
    //TODO(kjoshi): Investigate why this is needed anymore.
    for (Protos.TaskInfo taskInfo : cleanedTaskInfos) {
      stateStore.clearTask(taskInfo.getName());
      stateStore.storeTasks(Arrays.asList(taskInfo));
    }

    unneededTaskInfos.stream().forEach(taskInfo ->
        logger.info("Enqueued kill of taskName: {} taskId: {} due to unneeded taskinfos!",
            taskInfo.getName(),
            taskInfo.getTaskId()));

    taskIdsToKill.forEach(taskID -> TaskKiller.killTask(taskID));

    //WARNING: This is a global kill of pending tasks. This can interfere with
    //decommission steps which may have started up in pending state.
    //Some frameworks like k8s need decommission steps to execute correctly for cleanup.
    for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
      GoalStateOverride.Status overrideStatus = stateStore.fetchGoalOverrideStatus(taskInfo.getName());
      if (overrideStatus.progress == GoalStateOverride.Progress.PENDING) {
        logger.warn("Enqueued kill of taskName: {} taskId: {} due to GoalStateOverride PENDING!",
            taskInfo.getName(),
            taskInfo.getTaskId());
        // Enabling or disabling an override was triggered, but the task kill wasn't processed so that the
        // change in override could take effect. Kill the task so that it can enter (or exit) the override. The
        // override status will then be marked IN_PROGRESS once we have received the terminal TaskStatus.
        TaskKiller.killTask(taskInfo.getTaskId());
      }
    }
  }

  @Override
  protected OfferResponse processOffers(Collection<Protos.Offer> offers, Collection<Step> steps) {
    return processOffers(logger, planScheduler, launchRecorder, decommissionRecorder, offers, steps);
  }

  /**
   * Broken out into a separate function to facilitate direct testing.
   */
  @VisibleForTesting
  static OfferResponse processOffers(
      Logger logger,
      PlanScheduler planScheduler,
      PersistentLaunchRecorder launchRecorder,
      Optional<UninstallRecorder> decommissionRecorder,
      Collection<Protos.Offer> offers,
      Collection<Step> steps)
  {
    // See which offers are useful to the plans, then omit the ones that shouldn't be launched.
    List<OfferRecommendation> offerRecommendations = planScheduler.resourceOffers(offers, steps);

    if (!offers.isEmpty()) {
      logger.info("{} offer{} resulted in {} recommendation{}: {}",
          offers.size(),
          offers.size() == 1 ? "" : "s",
          offerRecommendations.size(),
          offerRecommendations.size() == 1 ? "" : "s",
          offerRecommendations.stream()
              .map(rec -> rec.getOfferId().getValue())
              .collect(Collectors.toSet()));
    }

    try {
      // Store any TaskInfo data in ZK:
      launchRecorder.record(offerRecommendations);
      if (decommissionRecorder.isPresent()) {
        // Notify decommission plan of dereservations:
        decommissionRecorder.get().recordDecommission(offerRecommendations);
      }
    } catch (Exception ex) {
      // Note: If a subset of operations were recorded, things could still be left in a bad state. However, in
      // practice any storage failure should have occurred in launchRecorder before any of the recommendations
      // were recorded. So in practice this record operation should be 'roughly atomic'.
      logger.error("Failed to record offer operations, returning empty operations list", ex);
      offerRecommendations = Collections.emptyList();
    }

    // Return the recommendations upstream so that any Operations can be sent to Mesos:
    return OfferResponse.processed(offerRecommendations);
  }

  /**
   * Returns the resources which are not expected by this service.
   *
   * <p>Resources can be unexpected for one of the following reasons:
   * <ul>
   * <li>The resource is from an old task and the service has since moved on (e.g. agent recently revived)</li>
   * <li>The resource is from a prior version of a replaced task (marked as permanently failed)</li>
   * <li>The resource is part of a task that's being decommissioned. In this case we also notify the decommission plan
   * that the resource is (about to be) cleaned.</li></ul>
   */
  @Override
  public UnexpectedResourcesResponse getUnexpectedResources(Collection<Protos.Offer> unusedOffers) {
    // First, determine which resource IDs we want to keep. Anything not listed here will be destroyed.
    final Set<String> resourceIdsToKeep;
    try {
      resourceIdsToKeep = stateStore.fetchTasks().stream()
          // A known task's resources should be kept if:
          // - the task is not marked as permanently failed, and
          // - the task is not in the process of being decommissioned
          .filter(taskInfo ->
              !FailureUtils.isPermanentlyFailed(taskInfo) &&
                  !stateStore.fetchGoalOverrideStatus(taskInfo.getName())
                      .equals(DecommissionPlanFactory.DECOMMISSIONING_STATUS))
          .map(taskInfo -> ResourceUtils.getResourceIds(ResourceUtils.getAllResources(taskInfo)))
          .flatMap(Collection::stream)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      logger.error("Failed to fetch expected tasks to determine unexpected resources", e);
      return UnexpectedResourcesResponse.failed(Collections.emptyList());
    }

    // Then, select any resources (and their parent offers) which are not in the above whitelist.
    Collection<OfferResources> unexpectedResources = new ArrayList<>();
    for (Protos.Offer offer : unusedOffers) {
      OfferResources unexpectedResourcesForOffer = new OfferResources(offer);
      for (Protos.Resource resource : offer.getResourcesList()) {
        Optional<String> resourceId = ResourceUtils.getResourceId(resource);
        if (resourceId.isPresent() && !resourceIdsToKeep.contains(resourceId.get())) {
          // This resource has a resource ID label, indicating that it's a reservation created by the SDK.
          // Mark it for automatic garbage collection.
          unexpectedResourcesForOffer.add(resource);
        }
      }
      if (!unexpectedResourcesForOffer.getResources().isEmpty()) {
        unexpectedResources.add(unexpectedResourcesForOffer);
      }
    }

    // Finally, notify any decommissionRecorder with the resources that are about to be unreserved/destroyed. In
    // practice this should be handled via the offer evaluation call above, but it can't hurt to also check here.
    if (decommissionRecorder.isPresent()) {
      try {
        decommissionRecorder.get().recordCleanupOrUninstall(unexpectedResources);
      } catch (Exception e) {
        // Failed to record the decommission. Refrain from returning these resources as unexpected for now, try
        // again later.
        logger.error("Failed to record unexpected resources in decommission recorder", e);
        return UnexpectedResourcesResponse.failed(Collections.emptyList());
      }
    }
    return UnexpectedResourcesResponse.processed(unexpectedResources);
  }

  @Override
  protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
    // Store status, then pass status to PlanManager => Plan => Steps
    String taskName = StateStoreUtils.fetchTaskInfo(stateStore, status).getName();

    // StateStore updates:
    // - TaskStatus
    // - Override status (if applicable)
    stateStore.storeStatus(taskName, status);

    // Notify plans of status update:
    planCoordinator.getPlanManagers().forEach(planManager -> planManager.update(status));

    // If the TaskStatus contains an IP Address, store it as a property in the StateStore.
    // We expect the TaskStatus to contain an IP address in both Host or CNI networking.
    // Currently, we are always _missing_ the IP Address on TASK_LOST. We always expect it on TASK_RUNNINGs
    if (status.hasContainerStatus() &&
        status.getContainerStatus().getNetworkInfosCount() > 0 &&
        status.getContainerStatus().getNetworkInfosList().stream()
            .anyMatch(networkInfo -> networkInfo.getIpAddressesCount() > 0))
    {
      // Map the TaskStatus to a TaskInfo. The map will throw a StateStoreException if no such TaskInfo exists.
      try {
        StateStoreUtils.storeTaskStatusAsProperty(stateStore, taskName, status);
      } catch (StateStoreException e) {
        logger.warn("Unable to store network info for status update: " + status, e);
      }
    }
  }

  /**
   * Creates a new {@link UninstallScheduler} based on the local components.  This is used to trigger uninstall of a
   * service without restarting the framework scheduler process. The created {@link UninstallScheduler} will
   * automatically flag the service's StateStore with an uninstall bit.
   */
  public UninstallScheduler toUninstallScheduler() {
    return new UninstallScheduler(
        this.serviceSpec,
        this.stateStore,
        this.configStore,
        this.schedulerConfig,
        this.planCustomizer,
        this.namespace);
  }
}
