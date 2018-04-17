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

    private final Logger logger;
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
                .map(taskName -> allTasks.get(taskName))
                .filter(taskInfo -> taskInfo != null)
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));

        for (int i = 0; i < offers.size(); ++i) {
            List<OfferEvaluationStage> evaluationStages =
                    getEvaluationPipeline(podInstanceRequirement, allTasks.values(), thisPodTasks);

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
                    getTargetConfig(podInstanceRequirement, thisPodTasks.values()),
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
                logger.info("Offer {}, {}: failed {} of {} evaluation stages:\n{}",
                        i + 1,
                        offer.getId().getValue(),
                        failedOutcomeCount,
                        evaluationStages.size(),
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
                logger.info("Offer {}: passed all {} evaluation stages, returning {} recommendations:\n{}",
                        i + 1, evaluationStages.size(), recommendations.size(), outcomeDetails.toString());

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
            evaluationPipeline.add(new ExecutorEvaluationStage(serviceName, Optional.empty()));
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
            evaluationStages.add(VolumeEvaluationStage.getNew(
                    volumeSpec, Optional.empty(), resourceNamespace));
        }

        String preReservedRole = null;
        String role = null;
        String principal = null;
        boolean shouldAddExecutorResources = true;
        for (Map.Entry<String, ResourceSet> entry : getNewResourceSets(podInstanceRequirement).entrySet()) {
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

                if (preReservedRole == null && role == null && principal == null) {
                    preReservedRole = resourceSpec.getPreReservedRole();
                    role = resourceSpec.getRole();
                    principal = resourceSpec.getPrincipal();
                }
            }

            for (VolumeSpec volumeSpec : entry.getValue().getVolumes()) {
                evaluationStages.add(VolumeEvaluationStage.getNew(
                        volumeSpec, Optional.of(taskName), resourceNamespace));
            }

            if (shouldAddExecutorResources) {
                // The default executor needs a constant amount of resources, account for them here.
                for (ResourceSpec resourceSpec : getExecutorResources(preReservedRole, role, principal)) {
                    evaluationStages.add(new ResourceEvaluationStage(
                            resourceSpec, Optional.empty(), Optional.empty(), resourceNamespace));
                }
                shouldAddExecutorResources = false;
            }


            // TLS evaluation stages should be added for all tasks regardless of the tasks to launch list to ensure
            // ExecutorInfo equality when launching new tasks
            for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
                if (!taskSpec.getTransportEncryption().isEmpty()) {
                    evaluationStages.add(tlsStageBuilder.get().build(taskSpec.getName()));
                }
            }

            if (!podInstanceRequirement.getPodInstance().getPod().getCapabilities().isEmpty()) {
                evaluationStages.add(new LinuxCapabilitiesEvaluationStage(taskName, resourceNamespace));
            }

            boolean shouldBeLaunched = podInstanceRequirement.getTasksToLaunch().contains(taskName);
            evaluationStages.add(
                    new LaunchEvaluationStage(serviceName, taskName, shouldBeLaunched));
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
        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            if (!taskSpec.getTransportEncryption().isEmpty()) {
                evaluationStages.add(tlsStageBuilder.get().build(taskSpec.getName()));
            }

            if (!podInstanceRequirement.getPodInstance().getPod().getCapabilities().isEmpty()) {
                evaluationStages.add(new LinuxCapabilitiesEvaluationStage(taskSpec.getName(), Optional.empty()));
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
                executorInfo.getResourcesList(),
                resourceNamespace);
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

            TaskResourceMapper taskResourceMapper =
                    new TaskResourceMapper(taskSpec, taskInfo, resourceNamespace);
            taskResourceMapper.getOrphanedResources()
                    .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));
            evaluationStages.addAll(taskResourceMapper.getEvaluationStages());

            boolean shouldLaunch = podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName());
            evaluationStages.add(
                    new LaunchEvaluationStage(serviceName, taskSpec.getName(), shouldLaunch));
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

    @VisibleForTesting
    UUID getTargetConfig(PodInstanceRequirement podInstanceRequirement, Collection<Protos.TaskInfo> taskInfos) {
        if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.NONE) || taskInfos.isEmpty()) {
            return targetConfigId;
        } else {
            // 1. Recovery always only handles tasks with a goal state of RUNNING
            // 2. All tasks in a pod should be launched with the same configuration
            // Therefore it is correct to take the target configuration of one task as being
            // representative of the whole of the pod. If tasks in the same pod with a goal
            // state of RUNNING had different target configurations this should be rectified
            // in any case, so it is doubly proper to choose a single target configuration as
            // representative of the whole pod's target configuration.

            Protos.TaskInfo taskInfo = taskInfos.stream().findFirst().get();
            try {
                return new TaskLabelReader(taskInfo).getTargetConfiguration();
            } catch (TaskException e) {
                logger.error(String.format(
                        "Falling back to current target configuration '%s'. " +
                                "Failed to determine target configuration for task: %s",
                                targetConfigId, TextFormat.shortDebugString(taskInfo)), e);
                return targetConfigId;
            }
        }
    }

}
