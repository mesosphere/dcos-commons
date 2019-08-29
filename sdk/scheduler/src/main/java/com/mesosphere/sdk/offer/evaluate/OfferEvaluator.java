package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.debug.OfferOutcomeTrackerV2;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.history.OfferOutcome;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Protos.Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to {@link PodInstanceRequirement}s.
 */
@SuppressWarnings({
    "checkstyle:MultipleStringLiterals",
    "checkstyle:HiddenField",
    "checkstyle:ThrowsCount",
    "checkstyle:FinalClass"
})
public class OfferEvaluator {

  private final Logger logger;

  private final FrameworkStore frameworkStore;

  private final StateStore stateStore;

  private final Optional<OfferOutcomeTracker> offerOutcomeTracker;

  private final Optional<OfferOutcomeTrackerV2> offerOutcomeTrackerV2;

  private final String serviceName;

  private final UUID targetConfigId;

  private final ArtifactQueries.TemplateUrlFactory templateUrlFactory;

  private final SchedulerConfig schedulerConfig;

  public OfferEvaluator(
      FrameworkStore frameworkStore,
      StateStore stateStore,
      Optional<OfferOutcomeTracker> offerOutcomeTracker,
      Optional<OfferOutcomeTrackerV2> offerOutcomeTrackerV2,
      String serviceName,
      UUID targetConfigId,
      ArtifactQueries.TemplateUrlFactory templateUrlFactory,
      SchedulerConfig schedulerConfig)
  {
    this.logger = LoggingUtils.getLogger(getClass());
    this.frameworkStore = frameworkStore;
    this.stateStore = stateStore;
    this.offerOutcomeTracker = offerOutcomeTracker;
    this.serviceName = serviceName;
    this.targetConfigId = targetConfigId;
    this.templateUrlFactory = templateUrlFactory;
    this.schedulerConfig = schedulerConfig;
    this.offerOutcomeTrackerV2 = offerOutcomeTrackerV2;
  }

  public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Protos.Offer> offers)
      throws InvalidRequirementException, IOException
  {
    // All tasks in the service (used by some PlacementRules):
    Map<String, Protos.TaskInfo> allTasks = stateStore.fetchTasks().stream()
        .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));
    // Preexisting tasks for this pod (if any):
    Map<String, Protos.TaskInfo> thisPodTasks =
        TaskUtils.getTaskNames(podInstanceRequirement.getPodInstance()).stream()
            .map(allTasks::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));

    // Evaluation stages are stateless, so we can reuse them when evaluating multiple offers.
    List<OfferEvaluationStage> evaluationStages =
        getEvaluationPipeline(podInstanceRequirement, allTasks.values(), thisPodTasks);

    for (int i = 0; i < offers.size(); ++i) {
      Protos.Offer offer = offers.get(i);

      MesosResourcePool resourcePool = new MesosResourcePool(
          offer, OfferEvaluationUtils.getRole(podInstanceRequirement.getPodInstance().getPod()));

      Map<TaskSpec, GoalStateOverride> overrideMap = new HashMap<>();
      for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
        GoalStateOverride override =
            stateStore.fetchGoalOverrideStatus(
                CommonIdUtils.getTaskInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
                .target;

        overrideMap.put(taskSpec, override);
      }

      PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
          podInstanceRequirement,
          serviceName,
          getTargetConfig(podInstanceRequirement, thisPodTasks),
          templateUrlFactory,
          schedulerConfig,
          thisPodTasks.values(),
          frameworkStore.fetchFrameworkId().get(),
          overrideMap);
      List<EvaluationOutcome> outcomes = new ArrayList<>();
      int failedOutcomeCount = 0;

      for (OfferEvaluationStage evaluationStage : evaluationStages) {
        EvaluationOutcome outcome = evaluationStage.evaluate(resourcePool, podInfoBuilder);
        outcomes.add(outcome);
        if (!outcome.isPassing()) {
          failedOutcomeCount++;
        }
      }

      StringBuilder outcomeDetails = new StringBuilder();
      List<String> outcomeReasons = new ArrayList<String>();
      for (EvaluationOutcome outcome : outcomes) {
        getOutcomes(outcomeReasons, outcome);
        logOutcome(outcomeDetails, outcome, "");
      }
      if (outcomeDetails.length() != 0) {
        // trim extra trailing newline:
        outcomeDetails.deleteCharAt(outcomeDetails.length() - 1);
      }

      if (failedOutcomeCount != 0) {
        logger.info("Offer {}, {}: failed {} of {} evaluation stages for {}:\n{}",
            i + 1,
            offer.getId().getValue(),
            failedOutcomeCount,
            evaluationStages.size(),
            podInstanceRequirement.getName(),
            outcomeDetails.toString());

        if (offerOutcomeTracker.isPresent()) {
          offerOutcomeTracker.get().track(new OfferOutcome(
              podInstanceRequirement.getName(),
              false,
              offer,
              outcomeDetails.toString()));
        }
        if (offerOutcomeTrackerV2.isPresent()) {
          offerOutcomeTrackerV2.get().getSummary().addOffer(new OfferOutcomeTrackerV2.OfferOutcomeV2(
              podInstanceRequirement.getName(),
              false,
              offer.toString(),
              outcomeReasons));
          offerOutcomeTrackerV2.get().getSummary().addFailureAgent(
              offer.getSlaveId().getValue());
          for (EvaluationOutcome outcome : outcomes) {
            if (!outcome.isPassing()) {
              offerOutcomeTrackerV2.get().getSummary().addFailureReason(
                  outcome.getSource());
            }
          }
        }
      } else {
        List<OfferRecommendation> recommendations = outcomes.stream()
            .map(outcome -> outcome.getOfferRecommendations())
            .flatMap(xs -> xs.stream())
            .collect(Collectors.toList());
        logger.info("Offer {}: passed all {} evaluation stages, returning {} recommendations for {}:\n{}",
            i + 1,
            evaluationStages.size(),
            recommendations.size(),
            podInstanceRequirement.getName(),
            outcomeDetails.toString());

        if (offerOutcomeTracker.isPresent()) {
          offerOutcomeTracker.get().track(new OfferOutcome(
              podInstanceRequirement.getName(),
              true,
              offer,
              outcomeDetails.toString()));
        }

        if (offerOutcomeTrackerV2.isPresent()) {
          offerOutcomeTrackerV2.get().getSummary().addOffer(new OfferOutcomeTrackerV2.OfferOutcomeV2(
              podInstanceRequirement.getName(),
              true,
              offer.toString(),
              outcomeReasons));
        }

        return recommendations;
      }
    }

    return Collections.emptyList();
  }

  public List<OfferEvaluationStage> getEvaluationPipeline(
      PodInstanceRequirement podInstanceRequirement,
      Collection<Protos.TaskInfo> allTasks,
      Map<String, Protos.TaskInfo> thisPodTasks) throws IOException
  {

    boolean noLaunchedTasksExist = thisPodTasks.values().stream()
        .flatMap(taskInfo -> taskInfo.getResourcesList().stream())
        .map(ResourceUtils::getResourceId)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .allMatch(String::isEmpty);

    boolean allTasksPermanentlyFailed = thisPodTasks.size() > 0 &&
        thisPodTasks.values().stream().allMatch(FailureUtils::isPermanentlyFailed);

    final String description;
    final boolean shouldGetNewRequirement;
    if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT) || allTasksPermanentlyFailed) {
      description = "failed";
      shouldGetNewRequirement = true;
    } else if (noLaunchedTasksExist) {
      description = "new";
      shouldGetNewRequirement = true;
    } else {
      description = "existing";
      shouldGetNewRequirement = false;
    }
    logger.info("Generating requirement for {} pod '{}' containing tasks: {}",
        description,
        podInstanceRequirement.getPodInstance().getName(),
        podInstanceRequirement.getTasksToLaunch());

    // Only create a TLS Evaluation Stage builder if the service actually uses TLS certs.
    // This avoids performing TLS cert generation in cases where the cluster may not support it (e.g. DC/OS Open).
    boolean anyTasksWithTLS = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
        .anyMatch(taskSpec -> !taskSpec.getTransportEncryption().isEmpty());
    Optional<TLSEvaluationStage.Builder> tlsStageBuilder = anyTasksWithTLS
        ? Optional.of(new TLSEvaluationStage.Builder(serviceName, schedulerConfig))
        : Optional.empty();

    List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();
    if (shouldGetNewRequirement) {
      evaluationPipeline.addAll(getNewEvaluationPipeline(podInstanceRequirement, allTasks, tlsStageBuilder));
    } else {
      Protos.ExecutorInfo executorInfo = getExecutorInfo(podInstanceRequirement, thisPodTasks.values());

      // An empty ExecutorID indicates we should use a new Executor, otherwise we should attempt to launch
      // tasks on an already running Executor.
      String executorIdString = executorInfo.getExecutorId().getValue();
      Optional<Protos.ExecutorID> executorID = executorIdString.isEmpty() ?
          Optional.empty() :
          Optional.of(executorInfo.getExecutorId());

      evaluationPipeline.add(new ExecutorEvaluationStage(serviceName, executorID));
      evaluationPipeline.addAll(getExistingEvaluationPipeline(
          podInstanceRequirement, thisPodTasks, allTasks, executorInfo, tlsStageBuilder));
    }

    return evaluationPipeline;
  }

  private Protos.ExecutorInfo getExecutorInfo(
      PodInstanceRequirement podInstanceRequirement,
      Collection<Protos.TaskInfo> taskInfos)
  {
    // Filter which tasks are candidates for executor reuse.  Don't try to reuse your own executor as it may
    // not be the latest one alive. Look for active executors that belong to the same Pod.
    List<String> taskNames = TaskUtils.getTaskNames(
        podInstanceRequirement.getPodInstance(),
        podInstanceRequirement.getTasksToLaunch());
    Collection<Protos.TaskInfo> executorReuseCandidates = taskInfos.stream()
        .filter(taskInfo -> !taskNames.contains(taskInfo.getName()))
        .collect(Collectors.toList());

    for (Protos.TaskInfo taskInfo : executorReuseCandidates) {
      if (taskHasReusableExecutor(taskInfo)) {
        logger.info("Using existing executor: {}", TextFormat.shortDebugString(taskInfo.getExecutor()));
        return taskInfo.getExecutor();
      }
    }

    // We set an empty ExecutorID to indicate that we are launching a new Executor, NOT reusing a currently running
    // one.
    Protos.TaskInfo taskInfo = taskInfos.stream().findFirst().get();
    Protos.ExecutorInfo executorInfo = taskInfo
        .getExecutor()
        .toBuilder()
        .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""))
        .build();
    logger.info("Using new executor derived from task {}: {}",
        taskInfo.getName(),
        TextFormat.shortDebugString(executorInfo));

    return executorInfo;
  }

  private boolean taskHasReusableExecutor(Protos.TaskInfo taskInfo) {
    Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(taskInfo.getName());
    if (!taskStatus.isPresent() || FailureUtils.isPermanentlyFailed(taskInfo)) {
      return false;
    }

    Protos.TaskState state = taskStatus.get().getState();
    switch (state) {
      case TASK_STAGING:
      case TASK_STARTING:
      case TASK_RUNNING:
        return true;
      default:
        return false;
    }
  }

  static void getOutcomes(List<String> outcomeReasons, EvaluationOutcome outcome) {
    String prefix = outcome.isPassing() ? "PASS" : "FAIL";
    outcomeReasons.add(String.format("%s(%s):%s", prefix, outcome.getSource(),
        outcome.getReason()));
    for (EvaluationOutcome child: outcome.getChildren()) {
      getOutcomes(outcomeReasons, child);
    }
  }

  static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
    stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
    for (EvaluationOutcome child : outcome.getChildren()) {
      logOutcome(stringBuilder, child, indent + "  ");
    }
  }

  private static List<ResourceSpec> getOrderedResourceSpecs(ResourceSet resourceSet) {
    // Statically defined ports, then dynamic ports, then everything else
    List<ResourceSpec> staticPorts = new ArrayList<>();
    List<ResourceSpec> dynamicPorts = new ArrayList<>();
    List<ResourceSpec> simpleResources = new ArrayList<>();

    for (ResourceSpec resourceSpec : resourceSet.getResources()) {
      if (resourceSpec instanceof PortSpec) {
        if (((PortSpec) resourceSpec).getPort() == 0) {
          dynamicPorts.add(resourceSpec);
        } else {
          staticPorts.add(resourceSpec);
        }
      } else {
        simpleResources.add(resourceSpec);
      }
    }

    List<ResourceSpec> resourceSpecs = new ArrayList<>();
    resourceSpecs.addAll(staticPorts);
    resourceSpecs.addAll(dynamicPorts);
    resourceSpecs.addAll(simpleResources);
    return resourceSpecs;
  }

  /**
   * Returns an evaluation pipeline for launching a new task or replacing a permanently failed task.
   * <p>
   * For relaunching a task at a previous location, or for launching against an existing executor,
   * {@code getExistingEvaluationPipeline} should be used instead.
   */
  private List<OfferEvaluationStage> getNewEvaluationPipeline(
      PodInstanceRequirement podInstanceRequirement,
      Collection<Protos.TaskInfo> allTasks,
      Optional<TLSEvaluationStage.Builder> tlsStageBuilder)
  {
    List<OfferEvaluationStage> evaluationStages = new ArrayList<>();
    evaluationStages.add(new ExecutorEvaluationStage(serviceName, Optional.empty()));

    if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
      evaluationStages.add(new PlacementRuleEvaluationStage(
          allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
    }

    for (VolumeSpec volumeSpec : podInstanceRequirement.getPodInstance().getPod().getVolumes()) {
      evaluationStages.add(VolumeEvaluationStage.getNew(volumeSpec, Collections.emptyList()));
    }

    // TLS evaluation stages should be added for all tasks regardless of the tasks to launch list to ensure
    // ExecutorInfo equality when launching new tasks
    if (tlsStageBuilder.isPresent()) {
      for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
        if (!taskSpec.getTransportEncryption().isEmpty()) {
          evaluationStages.add(tlsStageBuilder.get().build(taskSpec.getName()));
        }
      }
    }

    Map<String, ResourceSet> resourceSetsByTaskSpecName =
        podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
            // Create a TreeMap: Doesn't hurt to have consistent ordering when evaluating tasks
            .collect(Collectors.toMap(
                TaskSpec::getName,
                TaskSpec::getResourceSet,
                (u, v) -> {
                  throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                TreeMap::new));

    // Only reserve the executor's resources once:
    boolean addedExecutorResources = false;
    // For any given ResourceSet, only reserve its configured resources once (but store TaskInfos for all tasks):
    Set<String> addedResourceSets = new HashSet<>();

    for (Map.Entry<String, ResourceSet> taskEntry : resourceSetsByTaskSpecName.entrySet()) {
      String taskSpecName = taskEntry.getKey();
      ResourceSet resourceSet = taskEntry.getValue();
      List<ResourceSpec> resourceSpecs = getOrderedResourceSpecs(resourceSet);

      if (!addedExecutorResources) {
        // The default executor needs a fixed amount of "overhead" resources, to be added once per pod.
        // For consistency, let's put this before the per-task/resourceset RESERVE calls below.
        addedExecutorResources = true;

        getExecutorResourceSpecs(
            schedulerConfig,
            // All ResourceSpecs in a pod share the same role/principal, see YAMLToInternalMappers:
            resourceSpecs.get(0).getRole(),
            resourceSpecs.get(0).getPrincipal(),
            resourceSpecs.get(0).getPreReservedRole()).stream()
            .map(spec -> new ResourceEvaluationStage(
                spec,
                Collections.emptyList(),
                Optional.empty()))
            .forEach(evaluationStages::add);
      }

      if (!addedResourceSets.contains(resourceSet.getId())) {
        // Add evaluation stages for the resources in the task's resource set.
        // If multiple tasks share the same resource set, we only want to evaluate those shared resources once.

        // At the same time, we must also ensure that we update all of the relevant TaskInfos with the resource.
        Collection<String> taskNamesToUpdateProtos = resourceSetsByTaskSpecName.entrySet().stream()
            .filter(checkEntry -> resourceSet.getId().equals(checkEntry.getValue().getId()))
            .map(checkEntry -> checkEntry.getKey())
            .collect(Collectors.toSet());

        addedResourceSets.add(resourceSet.getId());

        for (ResourceSpec resourceSpec : resourceSpecs) {
          if (resourceSpec instanceof NamedVIPSpec) {
            evaluationStages.add(new NamedVIPEvaluationStage(
                (NamedVIPSpec) resourceSpec,
                taskNamesToUpdateProtos,
                Optional.empty()));
          } else if (resourceSpec instanceof PortSpec) {
            evaluationStages.add(new PortEvaluationStage(
                (PortSpec) resourceSpec, taskNamesToUpdateProtos, Optional.empty()));
          } else {
            evaluationStages.add(new ResourceEvaluationStage(
                resourceSpec, taskNamesToUpdateProtos, Optional.empty()));
          }
        }

        for (VolumeSpec volumeSpec : resourceSet.getVolumes()) {
          evaluationStages.add(VolumeEvaluationStage.getNew(volumeSpec, taskNamesToUpdateProtos));
        }
      }

      // Finally, either launch the task, or just update the StateStore with information about the task.
      boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskSpecName);
      evaluationStages.add(
          new LaunchEvaluationStage(serviceName, taskSpecName, shouldBeLaunched));
    }

    return evaluationStages;
  }

  private static Collection<ResourceSpec> getExecutorResourceSpecs(
      SchedulerConfig schedulerConfig, String role, String principal, String preReservedRole)
  {
    return schedulerConfig.getExecutorResources().entrySet().stream()
        .map(executorResourceEntry -> DefaultResourceSpec.newBuilder()
            .name(executorResourceEntry.getKey())
            .preReservedRole(preReservedRole)
            .role(role)
            .principal(principal)
            .value(executorResourceEntry.getValue())
            .build())
        .collect(Collectors.toList());
  }

  private List<OfferEvaluationStage> getExistingEvaluationPipeline(
      PodInstanceRequirement podInstanceRequirement,
      Map<String, Protos.TaskInfo> podTasks,
      Collection<Protos.TaskInfo> allTasks,
      Protos.ExecutorInfo executorInfo,
      Optional<TLSEvaluationStage.Builder> tlsStageBuilder)
  {
    List<OfferEvaluationStage> evaluationStages = new ArrayList<>();

    // TLS evaluation stages should be added for all tasks regardless of the tasks to launch list to ensure
    // ExecutorInfo equality when launching new tasks
    if (tlsStageBuilder.isPresent()) {
      for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
        if (!taskSpec.getTransportEncryption().isEmpty()) {
          evaluationStages.add(tlsStageBuilder.get().build(taskSpec.getName()));
        }
      }
    }

    if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent() &&
        podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT))
    {
      // If a "pod replace" was issued, ensure that the pod's new location follows any placement rules.
      evaluationStages.add(new PlacementRuleEvaluationStage(
          allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
    }

    // Select an arbitrary ResourceSpec from the pod definition to get the role and principal.
    // All ResourceSpecs in a pod share the same role/principal, see YAMLToInternalMappers.
    ResourceSpec resourceSpecForRoleAndPrincipal =
        podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
            .map(taskSpec -> taskSpec.getResourceSet().getResources())
            .filter(resourceSpecs -> !resourceSpecs.isEmpty())
            .findAny()
            .get()
            .iterator().next();
    // Add evaluation for the executor's own resources:
    ExecutorResourceMapper executorResourceMapper = new ExecutorResourceMapper(
        podInstanceRequirement.getPodInstance().getPod(),
        getExecutorResourceSpecs(
            schedulerConfig,
            resourceSpecForRoleAndPrincipal.getRole(),
            resourceSpecForRoleAndPrincipal.getPrincipal(),
            resourceSpecForRoleAndPrincipal.getPreReservedRole()),
        executorInfo.getResourcesList());
    executorResourceMapper.getOrphanedResources()
        .forEach(resource -> evaluationStages.add(new DestroyEvaluationStage(resource)));
    executorResourceMapper.getOrphanedResources()
        .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
    evaluationStages.addAll(executorResourceMapper.getEvaluationStages());

    // Evaluate any changes to the task(s):
    evaluationStages.addAll(getExistingTaskEvaluationPipeline(podInstanceRequirement, serviceName, podTasks));

    return evaluationStages;
  }

  /**
   * Returns the evaluation stages needed to relaunch a task. This may optionally include any autodetected changes
   * to the task's reserved resources.
   */
  private Collection<OfferEvaluationStage> getExistingTaskEvaluationPipeline(
      PodInstanceRequirement podInstanceRequirement,
      String serviceName,
      Map<String, Protos.TaskInfo> allTasksInPod)
  {
    Map<String, ResourceSet> allTaskSpecNamesToResourceSets =
        podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
            // Create a TreeMap: Doesn't hurt to have consistent ordering when evaluating tasks
            .collect(Collectors.toMap(
                TaskSpec::getName,
                TaskSpec::getResourceSet,
                (u, v) -> {
                  throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                TreeMap::new));

    // For each distinct Resource Set, ensure that we only evaluate their resources once. Multiple tasks may share
    // the same resource set, so we should avoid double-evaluating their common resources. However, we should update
    // the metadata for all tasks using a given resource set if or when its reservations are updated.

    // Note: It is possible that we're looking to reconfigure a ResourceSet that's still "attached" to a running
    // task. In practice, this isn't a problem because those resources will not be offered to us while they are
    // still occupied. We therefore do not need to worry about some kind of "collision" where we're trying modify
    // the reservations of a ResourceSet while they're still being occupied, which is an illegal operation.

    // For evaluation purposes, there are three categories of tasks to think about:
    // A. Tasks that are being launched: The ResourceSet for each of these tasks should be evaluated, and a launch
    //    operation should be produced.
    // B. Tasks that are not being launched, but share a ResourceSet with a task that is being launched: The
    //    metadata for these tasks should be updated to reflect any changes.
    // C. Tasks that are not being relaunched, and do not share a ResourceSet with any tasks that are being
    //    launched: Make no changes to these tasks, leave them as-is until a launch is requested in the future.

    // For any given ResourceSet, only evaluate its resources once (but update all affected TaskInfos):
    Set<String> updatedResourceSetIds = new HashSet<>();

    Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
    for (String taskSpecNameToLaunch : podInstanceRequirement.getTasksToLaunch()) {
      ResourceSet resourceSet = allTaskSpecNamesToResourceSets.get(taskSpecNameToLaunch);
      if (resourceSet == null) {
        throw new IllegalStateException(String.format(
            "Unable to find task to launch %s among defined tasks %s in pod %s. Malformed ServiceSpec?",
            taskSpecNameToLaunch,
            allTaskSpecNamesToResourceSets.keySet(),
            podInstanceRequirement.getName()));
      }
      if (updatedResourceSetIds.contains(resourceSet.getId())) {
        // Already updated. Maybe there are two tasks being launched into the same ResourceSet at the same time?
        // As a rule that shouldn't happen, but just in case...
        logger.warn("Multiple tasks to launch in pod instance requirement {} share the same resource set {}",
            podInstanceRequirement.getName(), resourceSet.getId());
        continue;
      }

      // If multiple tasks share the same resource set, we only want to evaluate those shared resources once.
      updatedResourceSetIds.add(resourceSet.getId());

      // Get the names of all tasks in this resource set.
      // We will update their TaskInfos and/or invoke launch operations for them.
      Collection<String> taskSpecNamesInResourceSet = allTaskSpecNamesToResourceSets.entrySet().stream()
          .filter(checkEntry -> resourceSet.getId().equals(checkEntry.getValue().getId()))
          .map(checkEntry -> checkEntry.getKey())
          // Go with alphabetical order by task spec name for consistency:
          .collect(Collectors.toCollection(TreeSet::new));

      // Add resource evaluations for the ResourceSet:
      evaluationStages.addAll(getExistingResourceSetStages(
          podInstanceRequirement,
          allTasksInPod,
          resourceSet,
          taskSpecNamesInResourceSet));
      // Add TaskInfo updates and/or launch operations for the task(s) paired with the resource set:
      for (String taskSpecName : taskSpecNamesInResourceSet) {
        evaluationStages.add(new LaunchEvaluationStage(
            serviceName,
            taskSpecName,
            podInstanceRequirement.getTasksToLaunch().contains(taskSpecName)));
      }
    }

    return evaluationStages;
  }

  /**
   * Returns the evaluation stages needed to update the reservations associated with a resource set. In the default
   * case, a resource set is 1:1 with a task, but services may also have multiple tasks that share a single resource
   * set.
   */
  private Collection<OfferEvaluationStage> getExistingResourceSetStages(
      PodInstanceRequirement podInstanceRequirement,
      Map<String, Protos.TaskInfo> allTasksInPod,
      ResourceSet resourceSet,
      Collection<String> taskSpecNamesInResourceSet)
  {
    // Search for any existing TaskInfo for one of the tasks in this resource set. The TaskInfo should have a copy
    // of the resources assigned to the resource set.
    Collection<String> taskInfoNames = taskSpecNamesInResourceSet.stream()
        .map(taskSpecName ->
            CommonIdUtils.getTaskInstanceName(podInstanceRequirement.getPodInstance(), taskSpecName))
        .collect(Collectors.toList());
    Optional<Protos.TaskInfo> taskInfo = taskInfoNames.stream()
        .map(allTasksInPod::get)
        .filter(Objects::nonNull)
        .findAny();
    if (!taskInfo.isPresent()) {
      // This shouldn't happen, because this codepath is for reevaluating pods that had been launched
      // before. There should always be at least one TaskInfo for the resource set...
      logger.error("Failed to find existing TaskInfo among {}, cannot evaluate existing resource set {}",
          taskInfoNames, resourceSet.getId());
      return Collections.emptyList();
    }

    TaskResourceMapper taskResourceMapper =
        new TaskResourceMapper(taskSpecNamesInResourceSet, resourceSet, taskInfo.get());

    Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
    taskResourceMapper.getOrphanedResources()
        .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
    evaluationStages.addAll(taskResourceMapper.getEvaluationStages());
    return evaluationStages;
  }

  /**
   * Returns a reasonable configuration ID to be used when launching tasks in a pod.
   *
   * @param podInstanceRequirement the pod requirement describing the pod being evaluated and the tasks to be
   *                               launched within the pod
   * @param thisPodTasksByName     all TaskInfos for the pod that currently exist (some may be old)
   * @return a config UUID to be used for the pod. In a config update this would be the target config, and in a
   * recovery operation this would be the pod's/tasks's current config
   */
  @VisibleForTesting
  UUID getTargetConfig(
      PodInstanceRequirement podInstanceRequirement,
      Map<String, Protos.TaskInfo> thisPodTasksByName)
  {
    if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.NONE)) {
      // This is a config update, the pod should use the new/current target config.
      return targetConfigId;
    }

    // This is a recovery operation. Reuse the pod's current configuration, and specifically avoid out-of-band
    // config updates as part of recovering the pod. Select the correct configuration to use for the recovery:
    RecoveryConfigIDs recoveryConfigIDs = new RecoveryConfigIDs(logger, podInstanceRequirement, thisPodTasksByName);

    Optional<UUID> selectedConfig = recoveryConfigIDs.selectRecoveryConfigID();
    if (!selectedConfig.isPresent()) {
      // Fall back to using the scheduler target config. This shouldn't happen (how are we recovering tasks
      // that have never been launched before?), but just in case...
      logger.error("No target configuration could be determined for recovering {}, using scheduler target {}",
          podInstanceRequirement.getName(), targetConfigId);
      selectedConfig = Optional.of(targetConfigId);
    }
    logger.info("Recovering {} with config {} ({})",
        podInstanceRequirement.getName(), selectedConfig.get(), recoveryConfigIDs);
    return selectedConfig.get();
  }

  /**
   * Implementation for selecting the configuration ID to use when recovering task(s) in a pod:
   *
   * <ol><li>Filter the pod's tasks to just the ones being recovered in this operation.</li>
   * <li>If multiple tasks are being recovered, prefer ones that are marked RUNNING, as they are more consistently
   * updated to new config ids (workaround for DCOS-42539).</li>
   * <li>If no tasks are found (shouldn't happen?), fall back to using the scheduler's target config.</li></ol>
   */
  private static class RecoveryConfigIDs {
    private final Logger logger;

    private final Map<String, UUID> runningConfigIDs = new TreeMap<>();

    private final Map<String, UUID> otherConfigIDs = new TreeMap<>();

    /**
     * Selects the tasks to be launched in a recovery operation, then groups their config UUIDs according to their
     * goal states. Tasks whose goal state is RUNNING get priority over other tasks when determining a reasonable
     * target.
     *
     * @param podInstanceRequirement the pod requirement describing the pod being recovered and the tasks to be
     *                               relaunched within the pod
     * @param existingPodTasksByName all TaskInfos for the pod that currently exist (some may be defunct)
     */
    private RecoveryConfigIDs(
        Logger logger,
        PodInstanceRequirement podInstanceRequirement,
        Map<String, Protos.TaskInfo> existingPodTasksByName)
    {
      this.logger = logger;
      for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
        if (!podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())) {
          // Task isn't included in the recovery operation, skip.
          continue;
        }
        final String taskName = CommonIdUtils.getTaskInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
        Protos.TaskInfo taskInfo = existingPodTasksByName.get(taskName);
        if (taskInfo == null) {
          // Task hasn't been launched yet, but is marked to be recovered...
          logger.warn("TaskInfo not found for recovering task '{}', available tasks are: {}",
              taskName, existingPodTasksByName.keySet());
          continue;
        }

        final UUID taskTarget;
        try {
          taskTarget = new TaskLabelReader(taskInfo).getTargetConfiguration();
        } catch (TaskException e) {
          logger.warn(
              String.format("Failed to determine target configuration for task: %s", taskName),
              e);
          continue;
        }

        if (GoalState.RUNNING.equals(taskSpec.getGoal())) {
          // Running tasks have first priority: Should contain the more recent config ID.
          runningConfigIDs.put(taskName, taskTarget);
        } else {
          // Other tasks like FINISHED/ONCE have second priority: Not as consistently updated in rollouts.
          otherConfigIDs.put(taskName, taskTarget);
        }
      }
    }

    /**
     * Returns an existing config ID to use for recovering the task(s), or an empty Optional if no valid config ID
     * could be found.
     */
    private Optional<UUID> selectRecoveryConfigID() {
      // Running tasks have first priority: Should contain the more recent config ID.
      Optional<UUID> selectedConfig = selectID(runningConfigIDs, "RUNNING");
      if (selectedConfig.isPresent()) {
        return selectedConfig;
      }
      // Other tasks like FINISHED/ONCE have second priority: Not consistently updated with config rollouts.
      return selectID(otherConfigIDs, "non-RUNNING");
    }

    private Optional<UUID> selectID(Map<String, UUID> configIDs, String taskTypeToLog) {
      if (configIDs.values().stream().distinct().count() > 1) {
        logger.warn("Multiple {} tasks with different target configs. Selecting random config: {}",
            taskTypeToLog, configIDs);
      }
      return configIDs.values().stream().findAny();
    }

    @Override
    public String toString() {
      return String.format("goal-running=%s, other=%s", runningConfigIDs, otherConfigIDs);
    }
  }
}
