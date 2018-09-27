package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.history.OfferOutcome;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Protos.Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to {@link PodInstanceRequirement}s.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;
    private final OfferOutcomeTracker offerOutcomeTracker;
    private final String serviceName;
    private final UUID targetConfigId;
    private final SchedulerConfig schedulerConfig;
    private final boolean useDefaultExecutor;

    public OfferEvaluator(
            StateStore stateStore,
            OfferOutcomeTracker offerOutcomeTracker,
            String serviceName,
            UUID targetConfigId,
            SchedulerConfig schedulerConfig,
            boolean useDefaultExecutor) {
        this.stateStore = stateStore;
        this.offerOutcomeTracker = offerOutcomeTracker;
        this.serviceName = serviceName;
        this.targetConfigId = targetConfigId;
        this.schedulerConfig = schedulerConfig;
        this.useDefaultExecutor = useDefaultExecutor;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Protos.Offer> offers)
            throws InvalidRequirementException, IOException {
        // All tasks in the service (used by some PlacementRules):
        Map<String, Protos.TaskInfo> allTasks = stateStore.fetchTasks().stream()
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));
        // Preexisting tasks for this pod (if any):
        Map<String, Protos.TaskInfo> thisPodTasks =
                TaskUtils.getTaskNames(podInstanceRequirement.getPodInstance()).stream()
                .map(taskName -> allTasks.get(taskName))
                .filter(taskInfo -> taskInfo != null)
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));

        for (int i = 0; i < offers.size(); ++i) {
            List<OfferEvaluationStage> evaluationStages =
                    getEvaluationPipeline(podInstanceRequirement, allTasks.values(), thisPodTasks);

            Protos.Offer offer = offers.get(i);

            MesosResourcePool resourcePool = new MesosResourcePool(
                    offer,
                    OfferEvaluationUtils.getRole(podInstanceRequirement.getPodInstance().getPod()));

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
                    schedulerConfig,
                    thisPodTasks.values(),
                    stateStore.fetchFrameworkId().get(),
                    useDefaultExecutor,
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
                logger.info("Offer {}, {}: failed {} of {} evaluation stages:\n{}",
                        i + 1,
                        offer.getId().getValue(),
                        failedOutcomeCount,
                        evaluationStages.size(),
                        outcomeDetails.toString());

                offerOutcomeTracker.track(new OfferOutcome(
                        podInstanceRequirement.getName(),
                        false,
                        offer,
                        outcomeDetails.toString()));
            } else {
                List<OfferRecommendation> recommendations = outcomes.stream()
                        .map(outcome -> outcome.getOfferRecommendations())
                        .flatMap(xs -> xs.stream())
                        .collect(Collectors.toList());
                logger.info("Offer {}: passed all {} evaluation stages, returning {} recommendations:\n{}",
                        i + 1, evaluationStages.size(), recommendations.size(), outcomeDetails.toString());

                offerOutcomeTracker.track(new OfferOutcome(
                        podInstanceRequirement.getName(),
                        true,
                        offer,
                        outcomeDetails.toString()));

                return recommendations;
            }
        }

        return Collections.emptyList();
    }

    public List<OfferEvaluationStage> getEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> allTasks,
            Map<String, Protos.TaskInfo> thisPodTasks) throws IOException {

        boolean noLaunchedTasksExist = thisPodTasks.values().stream()
                .flatMap(taskInfo -> taskInfo.getResourcesList().stream())
                .map(resource -> ResourceUtils.getResourceId(resource))
                .filter(resourceId -> resourceId.isPresent())
                .map(Optional::get)
                .filter(resourceId -> !resourceId.isEmpty())
                .count() == 0;

        boolean allTasksFailed =
                thisPodTasks.size() > 0 &&
                thisPodTasks.values().stream().allMatch(taskInfo -> FailureUtils.isPermanentlyFailed(taskInfo));

        final String description;
        final boolean shouldGetNewRequirement;
        if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT) || allTasksFailed) {
            description = "failed";
            shouldGetNewRequirement = true;
        } else if (noLaunchedTasksExist) {
            description = "new";
            shouldGetNewRequirement = true;
        } else {
            description = "existing";
            shouldGetNewRequirement = false;
        }
        logger.info("Generating requirement for {} pod '{}' containing tasks: {}.",
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
            evaluationPipeline.add(new ExecutorEvaluationStage(Optional.empty()));
            evaluationPipeline.addAll(getNewEvaluationPipeline(podInstanceRequirement, allTasks, tlsStageBuilder));
        } else {
            Protos.ExecutorInfo executorInfo = getExecutorInfo(podInstanceRequirement, thisPodTasks.values());

            // An empty ExecutorID indicates we should use a new Executor, otherwise we should attempt to launch
            // tasks on an already running Executor.
            String executorIdString = executorInfo.getExecutorId().getValue();
            Optional<Protos.ExecutorID> executorID = executorIdString.isEmpty() ?
                    Optional.empty() :
                    Optional.of(executorInfo.getExecutorId());

            evaluationPipeline.add(new ExecutorEvaluationStage(executorID));
            evaluationPipeline.addAll(getExistingEvaluationPipeline(
                    podInstanceRequirement, thisPodTasks, allTasks, executorInfo, tlsStageBuilder));
        }

        return evaluationPipeline;
    }

    private Protos.ExecutorInfo getExecutorInfo(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> taskInfos) {
        // Filter which tasks are candidates for executor reuse.  Don't try to reuse your own executor.
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
        Protos.ExecutorInfo executorInfo = taskInfos.stream().findFirst().get()
                .getExecutor().toBuilder()
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""))
                .build();
        logger.info("Using old executor: {}", TextFormat.shortDebugString(executorInfo));

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

    static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
        stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
        for (EvaluationOutcome child : outcome.getChildren()) {
            logOutcome(stringBuilder, child, indent + "  ");
        }
    }

    private static Map<String, ResourceSet> getNewResourceSets(PodInstanceRequirement podInstanceRequirement) {
        Map<String, ResourceSet> resourceSets =
                podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                        .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                        .collect(Collectors.toMap(TaskSpec::getName, TaskSpec::getResourceSet));

        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            if (resourceSets.keySet().contains(taskSpec.getName())) {
                continue;
            }

            Set<String> resourceSetNames = resourceSets.values().stream()
                    .map(resourceSet -> resourceSet.getId())
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

    private List<OfferEvaluationStage> getNewEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> allTasks,
            Optional<TLSEvaluationStage.Builder> tlsStageBuilder) {
        List<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
            evaluationStages.add(new PlacementRuleEvaluationStage(
                    allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
        }

        for (VolumeSpec volumeSpec : podInstanceRequirement.getPodInstance().getPod().getVolumes()) {
            evaluationStages.add(VolumeEvaluationStage.getNew(volumeSpec, null, useDefaultExecutor));
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

        String preReservedRole = null;
        String role = null;
        String principal = null;
        boolean shouldAddExecutorResources = useDefaultExecutor;
        for (Map.Entry<String, ResourceSet> entry : getNewResourceSets(podInstanceRequirement).entrySet()) {
            String taskName = entry.getKey();
            List<ResourceSpec> resourceSpecs = getOrderedResourceSpecs(entry.getValue());

            for (ResourceSpec resourceSpec : resourceSpecs) {
                if (resourceSpec instanceof NamedVIPSpec) {
                    evaluationStages.add(
                            new NamedVIPEvaluationStage((NamedVIPSpec) resourceSpec, taskName, Optional.empty()));
                } else if (resourceSpec instanceof PortSpec) {
                    evaluationStages.add(new PortEvaluationStage((PortSpec) resourceSpec, taskName, Optional.empty()));
                } else {
                    evaluationStages.add(new ResourceEvaluationStage(resourceSpec, Optional.empty(), taskName));
                }

                if (preReservedRole == null && role == null && principal == null) {
                    preReservedRole = resourceSpec.getPreReservedRole();
                    role = resourceSpec.getRole();
                    principal = resourceSpec.getPrincipal();
                }
            }

            for (VolumeSpec volumeSpec : entry.getValue().getVolumes()) {
                evaluationStages.add(VolumeEvaluationStage.getNew(volumeSpec, taskName, useDefaultExecutor));
            }

            if (shouldAddExecutorResources) {
                // The default executor needs a constant amount of resources, account for them here.
                for (ResourceSpec resourceSpec : getExecutorResources(preReservedRole, role, principal)) {
                    evaluationStages.add(new ResourceEvaluationStage(resourceSpec, Optional.empty(), null));
                }
                shouldAddExecutorResources = false;
            }


            boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskName);
            evaluationStages.add(new LaunchEvaluationStage(taskName, shouldBeLaunched, useDefaultExecutor));
        }

        return evaluationStages;
    }

    private static List<ResourceSpec> getExecutorResources(String preReservedRole, String role, String principal) {
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

    private List<OfferEvaluationStage> getExistingEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Map<String, Protos.TaskInfo> podTasks,
            Collection<Protos.TaskInfo> allTasks,
            Protos.ExecutorInfo executorInfo,
            Optional<TLSEvaluationStage.Builder> tlsStageBuilder) {
        List<TaskSpec> taskSpecs = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                .collect(Collectors.toList());

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
            evaluationStages.add(new PlacementRuleEvaluationStage(
                    allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
        }

        ResourceSpec firstResource = taskSpecs.get(0).getResourceSet().getResources().iterator().next();
        String preReservedRole = firstResource.getPreReservedRole();
        String role = firstResource.getRole();
        String principal = firstResource.getPrincipal();

        ExecutorResourceMapper executorResourceMapper = new ExecutorResourceMapper(
                podInstanceRequirement.getPodInstance().getPod(),
                getExecutorResources(preReservedRole, role, principal),
                executorInfo,
                useDefaultExecutor);
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new DestroyEvaluationStage(resource)));
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
        evaluationStages.addAll(executorResourceMapper.getEvaluationStages());

        for (TaskSpec taskSpec : taskSpecs) {
            String taskInstanceName =
                    TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName());
            Protos.TaskInfo taskInfo =
                    getTaskInfoSharingResourceSet(podInstanceRequirement.getPodInstance(), taskSpec, podTasks);
            if (taskInfo == null) {
                logger.error("Failed to fetch task {}.  Cannot generate resource map.", taskInstanceName);
                return Collections.emptyList();
            }

            TaskResourceMapper taskResourceMapper = new TaskResourceMapper(taskSpec, taskInfo, useDefaultExecutor);
            taskResourceMapper.getOrphanedResources()
                    .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
            evaluationStages.addAll(taskResourceMapper.getEvaluationStages());

            boolean shouldLaunch = podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName());
            evaluationStages.add(new LaunchEvaluationStage(taskSpec.getName(), shouldLaunch, useDefaultExecutor));
        }

        return evaluationStages;
    }

    private static Protos.TaskInfo getTaskInfoSharingResourceSet(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, Protos.TaskInfo> podTasks) {

        String taskInfoName = TaskSpec.getInstanceName(podInstance, taskSpec.getName());
        Protos.TaskInfo taskInfo = podTasks.get(taskInfoName);
        if (taskInfo != null) {
            return taskInfo;
        }

        String resourceSetId = taskSpec.getResourceSet().getId();
        List<String> sharedTaskNames = podInstance.getPod().getTasks().stream()
                .filter(ts -> ts.getResourceSet().getId().equals(resourceSetId))
                .map(ts -> TaskSpec.getInstanceName(podInstance, ts.getName()))
                .collect(Collectors.toList());

        for (String taskName : sharedTaskNames) {
            taskInfo = podTasks.get(taskName);
            if (taskInfo != null) {
                return taskInfo;
            }
        }

        return null;
    }

    /**
     * Returns a reasonable configuration ID to be used when launching tasks in a pod.
     *
     * @param podInstanceRequirement the pod requirement describing the pod being evaluated and the tasks to be
     *     launched within the pod
     * @param thisPodTasksByName all TaskInfos for the pod that currently exist (some may be old)
     * @return a config UUID to be used for the pod. In a config update this would be the target config, and in a
     *     recovery operation this would be the pod's/tasks's current config
     */
    @VisibleForTesting
    UUID getTargetConfig(
            PodInstanceRequirement podInstanceRequirement,
            Map<String, Protos.TaskInfo> thisPodTasksByName) {
        if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.NONE)) {
            // This is a config update, the pod should use the new/current target config.
            return targetConfigId;
        }

        // This is a recovery operation. Reuse the pod's current configuration, and specifically avoid out-of-band
        // config updates as part of recovering the pod. Select the correct configuration to use for the recovery:
        // 1. Filter the pod's tasks to just the ones being recovered in this operation.
        // 2. If multiple tasks are being recovered, prefer ones that are marked RUNNING, as they are more
        //    consistently updated to new config ids (workaround for DCOS-42539).
        // 3. If no tasks are found (shouldn't happen?), fall back to using the target config.
        Map<String, UUID> runningConfigTargets = new TreeMap<>();
        Map<String, UUID> otherConfigTargets = new TreeMap<>();
        sortRecoveryConfigTargets(podInstanceRequirement, thisPodTasksByName, runningConfigTargets, otherConfigTargets);

        // Select a config id from the goal state groups:
        if (runningConfigTargets.values().stream().distinct().count() > 1) {
            logger.warn("Multiple goal-running tasks with different target configs. Selecting random config: {}",
                    runningConfigTargets);
        }
        Optional<UUID> selectedConfig = runningConfigTargets.values().stream().findAny();
        if (!selectedConfig.isPresent()) {
            if (otherConfigTargets.values().stream().distinct().count() > 1) {
                logger.warn("Multiple goal-other tasks with different target configs. Selecting random config: {}",
                        otherConfigTargets);
            }
            selectedConfig = otherConfigTargets.values().stream().findAny();
        }
        if (!selectedConfig.isPresent()) {
            // No TaskInfos for the tasks being recovered? Missing target config ids?
            logger.error("No target configuration could be determined for recovering {}, using scheduler target {} " +
                    "(goal-running={}, other={})",
                    podInstanceRequirement.getName(),
                    targetConfigId,
                    runningConfigTargets,
                    otherConfigTargets);
            return targetConfigId;
        }
        logger.info("Recovering {} with config {} (goal-running={}, other={})",
                podInstanceRequirement.getName(), selectedConfig.get(), runningConfigTargets, otherConfigTargets);
        return selectedConfig.get();
    }

    /**
     * Produces the tasks to be launched in a recovery operation, then groups their config UUIDs according to their goal
     * states. Tasks whose goal state is RUNNING get priority over other tasks when determining a reasonable target.
     *
     * @param podInstanceRequirement the pod requirement describing the pod being recovered and the tasks to be
     *     relaunched within the pod
     * @param thisPodTasksByName all TaskInfos for the pod that currently exist (some may be old)
     * @param runningConfigTargets output where config ids for tasks with a RUNNING GoalState are placed
     * @param otherConfigTargets output where config ids for tasks with non-RUNNING GoalStates are placed
     */
    private static void sortRecoveryConfigTargets(
            PodInstanceRequirement podInstanceRequirement,
            Map<String, Protos.TaskInfo> thisPodTasksByName,
            Map<String, UUID> runningConfigTargets,
            Map<String, UUID> otherConfigTargets) {
        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            if (!podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())) {
                // Task isn't included in the recovery operation, skip.
                logger.info("TODO skipping " + taskSpec.getName() + " vs " + podInstanceRequirement.getTasksToLaunch());
                continue;
            }
            final String taskName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
            Protos.TaskInfo taskInfo = thisPodTasksByName.get(taskName);
            if (taskInfo == null) {
                // Task hasn't been launched yet, but is marked to be recovered...
                logger.warn("TaskInfo not found for recovering task '{}', available tasks are: {}",
                        taskName, thisPodTasksByName.keySet());
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

            if (taskSpec.getGoal().equals(GoalState.RUNNING)) {
                // Running tasks have first priority: Should contain the more recent config ID.
                runningConfigTargets.put(taskName, taskTarget);
            } else {
                // FINISHED/ONCE tasks have second priority: Not consistently updated with config rollouts.
                otherConfigTargets.put(taskName, taskTarget);
            }
        }
    }
}
