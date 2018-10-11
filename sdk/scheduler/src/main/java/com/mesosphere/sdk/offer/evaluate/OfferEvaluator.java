package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.history.OfferOutcome;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

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

    final Logger logger;
    private final FrameworkStore frameworkStore;
    private final StateStore stateStore;
    private final Optional<OfferOutcomeTracker> offerOutcomeTracker;
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
            Optional<String> resourceNamespace) {
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

                logger.info("TODO recommendations are: {}", recommendations.stream()
                        .filter(r -> r.getOperation().isPresent())
                        .map(r -> r.getOperation().get())
                        .collect(Collectors.toList()));
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
            Collection<Protos.TaskInfo> taskInfos) {
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
                    volumeSpec, Optional.empty(), resourceNamespace));
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

        boolean shouldAddExecutorResources = true;
        for (Map.Entry<String, ResourceSet> taskEntry : getNewResourceSets(podInstanceRequirement).entrySet()) {
            String taskName = taskEntry.getKey();
            List<ResourceSpec> resourceSpecs = getOrderedResourceSpecs(taskEntry.getValue());

            if (shouldAddExecutorResources) {
                // The default executor needs a constant amount of resources, account for them here.
                // For consistency, let's put this before the per-task RESERVE calls.
                // Also, use an arbitrary ResourceSpec to figure out what roles/principal to use.
                getExecutorResourceSpecs(schedulerConfig, resourceSpecs.get(0)).stream()
                        .map(spec -> new ResourceEvaluationStage(
                                spec,
                                Optional.empty(),
                                Optional.empty(),
                                resourceNamespace))
                        .forEach(evaluationStages::add);
                shouldAddExecutorResources = false;
            }

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
            }

            for (VolumeSpec volumeSpec : taskEntry.getValue().getVolumes()) {
                evaluationStages.add(VolumeEvaluationStage.getNew(
                        volumeSpec, Optional.of(taskName), resourceNamespace));
            }

            boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskName);
            evaluationStages.add(
                    new LaunchEvaluationStage(serviceName, taskName, shouldBeLaunched));
        }

        return evaluationStages;
    }

    private static Collection<ResourceSpec> getExecutorResourceSpecs(
            SchedulerConfig schedulerConfig, ResourceSpec resourceSpecForRoles) {
        return schedulerConfig.getExecutorResources().entrySet().stream()
                .map(executorResourceEntry -> DefaultResourceSpec.newBuilder()
                        .name(executorResourceEntry.getKey())
                        .preReservedRole(resourceSpecForRoles.getPreReservedRole())
                        .role(resourceSpecForRoles.getRole())
                        .principal(resourceSpecForRoles.getPrincipal())
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

        ExistingTaskEvaluationPipeline existingTasks =
                new ExistingTaskEvaluationPipeline(stateStore, podInstanceRequirement);

        // Add evaluation for the executor's own resources:
        ExecutorResourceMapper executorResourceMapper = new ExecutorResourceMapper(
                podInstanceRequirement.getPodInstance().getPod(),
                getExecutorResourceSpecs(schedulerConfig, existingTasks.getRandomLaunchableResourceSpec()),
                executorInfo.getResourcesList(),
                resourceNamespace);
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new DestroyEvaluationStage(resource)));
        executorResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
        evaluationStages.addAll(executorResourceMapper.getEvaluationStages());

        // Evaluate any changes to the task(s):
        evaluationStages.addAll(existingTasks.getTaskStages(serviceName, resourceNamespace, podTasks));

        return evaluationStages;
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
         *     relaunched within the pod
         * @param existingPodTasksByName all TaskInfos for the pod that currently exist (some may be defunct)
         */
        private RecoveryConfigIDs(
                Logger logger,
                PodInstanceRequirement podInstanceRequirement,
                Map<String, Protos.TaskInfo> existingPodTasksByName) {
            this.logger = logger;
            for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
                if (!podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())) {
                    // Task isn't included in the recovery operation, skip.
                    continue;
                }
                final String taskName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
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
