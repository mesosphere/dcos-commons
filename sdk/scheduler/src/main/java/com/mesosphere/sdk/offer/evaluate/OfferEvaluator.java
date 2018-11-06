package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.offer.Constants;
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
import com.mesosphere.sdk.specification.PodInstance;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Protos.Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to {@link PodInstanceRequirement}s.
 */
public class OfferEvaluator {

  private final Logger logger;

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Protos.Offer> offers)
            throws InvalidRequirementException, IOException {
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
                                TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
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
            for (EvaluationOutcome outcome : outcomes) {
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

                return recommendations;
            }
        }

  private final StateStore stateStore;

    public List<OfferEvaluationStage> getEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> allTasks,
            Map<String, Protos.TaskInfo> thisPodTasks) throws IOException {

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

  private final String serviceName;

  private final UUID targetConfigId;

  private final ArtifactQueries.TemplateUrlFactory templateUrlFactory;

  private final SchedulerConfig schedulerConfig;

  private final Optional<String> resourceNamespace;

  public OfferEvaluator(
      FrameworkStore frameworkStore,
      StateStore stateStore,
      Optional<OfferOutcomeTracker> offerOutcomeTracker,
      String serviceName,
      UUID targetConfigId,
      ArtifactQueries.TemplateUrlFactory templateUrlFactory,
      SchedulerConfig schedulerConfig,
      Optional<String> resourceNamespace)
  {
    this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
    this.frameworkStore = frameworkStore;
    this.stateStore = stateStore;
    this.offerOutcomeTracker = offerOutcomeTracker;
    this.serviceName = serviceName;
    this.targetConfigId = targetConfigId;
    this.templateUrlFactory = templateUrlFactory;
    this.schedulerConfig = schedulerConfig;
    this.resourceNamespace = resourceNamespace;
  }

  static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
    stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
    for (EvaluationOutcome child : outcome.getChildren()) {
      logOutcome(stringBuilder, child, indent + "  ");
    }
  }

  private static Map<String, ResourceSet> getNewResourceSets(
      PodInstanceRequirement podInstanceRequirement)
  {
    Map<String, ResourceSet> resourceSets =
        podInstanceRequirement
            .getPodInstance()
            .getPod()
            .getTasks()
            .stream()
            .filter(taskSpec ->
                podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())
            )
            .collect(Collectors.toMap(TaskSpec::getName, TaskSpec::getResourceSet));

    for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
      if (resourceSets.keySet().contains(taskSpec.getName())) {
        continue;
      }

      Set<String> resourceSetNames = resourceSets.values().stream()
          .map(ResourceSet::getId)
          .collect(Collectors.toSet());

      if (!resourceSetNames.contains(taskSpec.getResourceSet().getId())) {
        resourceSets.put(taskSpec.getName(), taskSpec.getResourceSet());
      }
    }

    return resourceSets;
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

  private static List<ResourceSpec> getExecutorResources(
      String preReservedRole,
      String role,
      String principal)
  {
    List<ResourceSpec> resources = new ArrayList<>();

    resources.add(DefaultResourceSpec.newBuilder()
        .name(Constants.CPUS_RESOURCE_TYPE)
        .preReservedRole(preReservedRole)
        .role(role)
        .principal(principal)
        .value(scalar(Constants.DEFAULT_EXECUTOR_CPUS))
        .build());

    resources.add(DefaultResourceSpec.newBuilder()
        .name(Constants.MEMORY_RESOURCE_TYPE)
        .preReservedRole(preReservedRole)
        .role(role)
        .principal(principal)
        .value(scalar(Constants.DEFAULT_EXECUTOR_MEMORY))
        .build());

    resources.add(DefaultResourceSpec.newBuilder()
        .name(Constants.DISK_RESOURCE_TYPE)
        .preReservedRole(preReservedRole)
        .role(role)
        .principal(principal)
        .value(scalar(Constants.DEFAULT_EXECUTOR_DISK))
        .build());

    return resources;
  }

  private static Protos.Value scalar(double val) {
    Protos.Value.Builder builder = Protos.Value.newBuilder()
        .setType(Protos.Value.Type.SCALAR);
    builder.getScalarBuilder().setValue(val);
    return builder.build();
  }

  private static Protos.TaskInfo getTaskInfoSharingResourceSet(
      PodInstance podInstance,
      TaskSpec taskSpec,
      Map<String, Protos.TaskInfo> podTasks)
  {

    String taskInfoName = TaskSpec.getInstanceName(podInstance, taskSpec.getName());
    Protos.TaskInfo taskInfo = podTasks.get(taskInfoName);
    if (taskInfo != null) {
      return taskInfo;
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

  private List<OfferEvaluationStage> getNewEvaluationPipeline(
      PodInstanceRequirement podInstanceRequirement,
      Collection<Protos.TaskInfo> allTasks,
      Optional<TLSEvaluationStage.Builder> tlsStageBuilder)
  {
    List<OfferEvaluationStage> evaluationStages = new ArrayList<>();
    if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
      evaluationStages.add(new PlacementRuleEvaluationStage(
          allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
    }

    /**
     * Returns an evaluation pipeline for launching a new task or replacing a permanently failed task.
     *
     * For relaunching a task at a previous location, or for launching against an existing executor,
     * {@code getExistingEvaluationPipeline} should be used instead.
     */
    private List<OfferEvaluationStage> getNewEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> allTasks,
            Optional<TLSEvaluationStage.Builder> tlsStageBuilder) {
        List<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        evaluationStages.add(new ExecutorEvaluationStage(serviceName, Optional.empty()));

        if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
            evaluationStages.add(new PlacementRuleEvaluationStage(
                    allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
        }

        for (VolumeSpec volumeSpec : podInstanceRequirement.getPodInstance().getPod().getVolumes()) {
            evaluationStages.add(VolumeEvaluationStage.getNew(
                    volumeSpec, Collections.emptyList(), resourceNamespace));
        }
      }
    }

    String preReservedRole = null;
    String role = null;
    String principal = null;
    boolean shouldAddExecutorResources = true;
    for (Map.Entry<String, ResourceSet> entry :
        getNewResourceSets(podInstanceRequirement).entrySet())
    {
      String taskName = entry.getKey();
      List<ResourceSpec> resourceSpecs = getOrderedResourceSpecs(entry.getValue());

      for (ResourceSpec resourceSpec : resourceSpecs) {
        if (resourceSpec instanceof NamedVIPSpec) {
          evaluationStages.add(new NamedVIPEvaluationStage(
              (NamedVIPSpec) resourceSpec, taskName, Optional.empty(), resourceNamespace));
        } else if (resourceSpec instanceof PortSpec) {
          evaluationStages.add(new PortEvaluationStage(
              (PortSpec) resourceSpec, taskName, Optional.empty(), resourceNamespace));
        } else {
          evaluationStages.add(new ResourceEvaluationStage(
              resourceSpec, Optional.of(taskName), Optional.empty(), resourceNamespace));
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
                                Optional.empty(),
                                resourceNamespace))
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
                                Optional.empty(),
                                resourceNamespace));
                    } else if (resourceSpec instanceof PortSpec) {
                        evaluationStages.add(new PortEvaluationStage(
                                (PortSpec) resourceSpec, taskNamesToUpdateProtos, Optional.empty(), resourceNamespace));
                    } else {
                        evaluationStages.add(new ResourceEvaluationStage(
                                resourceSpec, taskNamesToUpdateProtos, Optional.empty(), resourceNamespace));
                    }
                }

                for (VolumeSpec volumeSpec : resourceSet.getVolumes()) {
                    evaluationStages.add(VolumeEvaluationStage.getNew(
                            volumeSpec, taskNamesToUpdateProtos, resourceNamespace));
                }
            }

            // Finally, either launch the task, or just update the StateStore with information about the task.
            boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskSpecName);
            evaluationStages.add(
                    new LaunchEvaluationStage(serviceName, taskSpecName, shouldBeLaunched));
        }
        shouldAddExecutorResources = false;
      }

      boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskName);
      evaluationStages.add(
          new LaunchEvaluationStage(serviceName, taskName, shouldBeLaunched));
    }

    private static Collection<ResourceSpec> getExecutorResourceSpecs(
            SchedulerConfig schedulerConfig, String role, String principal, String preReservedRole) {
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
            Optional<TLSEvaluationStage.Builder> tlsStageBuilder) {
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
                podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT)) {
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
                executorInfo.getResourcesList(),
                resourceNamespace);
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new DestroyEvaluationStage(resource)));
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
        evaluationStages.addAll(executorResourceMapper.getEvaluationStages());

        // Evaluate any changes to the task(s):
        evaluationStages.addAll(getExistingTaskEvaluationPipeline(
                podInstanceRequirement, serviceName, resourceNamespace, podTasks));

        return evaluationStages;
    }

    /**
     * Returns the evaluation stages needed to relaunch a task. This may optionally include any autodetected changes
     * to the task's reserved resources.
     */
    private Collection<OfferEvaluationStage> getExistingTaskEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            Optional<String> resourceNamespace,
            Map<String, Protos.TaskInfo> allTasksInPod) {
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
                    serviceName,
                    resourceNamespace,
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

      boolean shouldLaunch = podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName());
      evaluationStages.add(
          new LaunchEvaluationStage(serviceName, taskSpec.getName(), shouldLaunch));
    }

    /**
     * Returns the evaluation stages needed to update the reservations associated with a resource set. In the default
     * case, a resource set is 1:1 with a task, but services may also have multiple tasks that share a single resource
     * set.
     */
    private Collection<OfferEvaluationStage> getExistingResourceSetStages(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            Optional<String> resourceNamespace,
            Map<String, Protos.TaskInfo> allTasksInPod,
            ResourceSet resourceSet,
            Collection<String> taskSpecNamesInResourceSet) {
        // Search for any existing TaskInfo for one of the tasks in this resource set. The TaskInfo should have a copy
        // of the resources assigned to the resource set.
        Collection<String> taskInfoNames = taskSpecNamesInResourceSet.stream()
                .map(taskSpecName ->
                        TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpecName))
                .collect(Collectors.toList());
        Optional<Protos.TaskInfo> taskInfo = taskInfoNames.stream()
                .map(taskInfoName -> allTasksInPod.get(taskInfoName))
                .filter(mapTaskInfo -> mapTaskInfo != null)
                .findAny();
        if (!taskInfo.isPresent()) {
            // This shouldn't happen, because this codepath is for reevaluating pods that had been launched
            // before. There should always be at least one TaskInfo for the resource set...
            logger.error("Failed to find existing TaskInfo among {}, cannot evaluate existing resource set {}",
                    taskInfoNames, resourceSet.getId());
            return Collections.emptyList();
        }

        TaskResourceMapper taskResourceMapper =
                new TaskResourceMapper(taskSpecNamesInResourceSet, resourceSet, taskInfo.get(), resourceNamespace);

        Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        taskResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
        evaluationStages.addAll(taskResourceMapper.getEvaluationStages());
        return evaluationStages;
    }

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
        final String taskName =
            TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
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
