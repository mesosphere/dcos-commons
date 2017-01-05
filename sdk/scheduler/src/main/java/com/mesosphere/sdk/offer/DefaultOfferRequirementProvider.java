package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.*;

/**
 * A default implementation of the OfferRequirementProvider interface.
 */
public class DefaultOfferRequirementProvider implements OfferRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOfferRequirementProvider.class);
    private static final String EXECUTOR_URI = "EXECUTOR_URI";
    private static final String LIBMESOS_URI = "LIBMESOS_URI";
    private static final String JAVA_URI = "JAVA_URI";
    private static final String DEFAULT_JAVA_URI = "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz";

    private static final String POD_INSTANCE_INDEX_KEY = "POD_INSTANCE_INDEX";

    private final StateStore stateStore;
    private final UUID targetConfigurationId;

    /**
     * Creates a new instance which relies on the provided {@link StateStore} for storing known tasks, and which
     * updates tasks which are not tagged with the provided {@code targetConfigurationId}.
     */
    public DefaultOfferRequirementProvider(StateStore stateStore, UUID targetConfigurationId) {
        this.stateStore = stateStore;
        this.targetConfigurationId = targetConfigurationId;
    }

    @Override
    public OfferRequirement getNewOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException {
        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                getNewTaskInfos(
                        podInstance,
                        tasksToLaunch,
                        targetConfigurationId),
                Optional.of(getExecutor(podInstance)),
                podInstance.getPod().getPlacementRule());
    }

    @Override
    public OfferRequirement getExistingOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException {
        List<TaskSpec> taskSpecs = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
                .collect(Collectors.toList());
        Map<Protos.TaskInfo, TaskSpec> taskMap = new HashMap<>();

        for (TaskSpec taskSpec : taskSpecs) {
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(
                    TaskSpec.getInstanceName(podInstance, taskSpec));
            if (taskInfoOptional.isPresent()) {
                taskMap.put(taskInfoOptional.get(), taskSpec);
            } else {
                Protos.TaskInfo taskInfo = getNewTaskInfo(
                        podInstance,
                        taskSpec,
                        targetConfigurationId,
                        StateStoreUtils.getResources(stateStore, podInstance, taskSpec));
                LOGGER.info("Generated new TaskInfo: {}", TextFormat.shortDebugString(taskInfo));
                taskMap.put(taskInfo, taskSpec);
            }
        }

        if (taskMap.size() == 0) {
            LOGGER.warn("Attempting to get existing OfferRequirement generated 0 tasks.");
        }

        List<TaskRequirement> taskRequirements = new ArrayList<>();
        for (Map.Entry<Protos.TaskInfo, TaskSpec> e : taskMap.entrySet()) {
            taskRequirements.add(
                    getExistingTaskRequirement(podInstance, e.getKey(), e.getValue(), targetConfigurationId));
        }
        validateTaskRequirements(taskRequirements);

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskRequirements,
                ExecutorRequirement.create(getExecutor(podInstance)),
                podInstance.getPod().getPlacementRule());
    }

    private static List<Protos.TaskInfo> getNewTaskInfos(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            UUID targetConfigurationId) throws InvalidRequirementException {

        LOGGER.info("Getting new TaskInfos for tasks: {}", tasksToLaunch);

        ArrayList<String> usedResourceSets = new ArrayList<>();
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();

        // Generating TaskInfos to launch.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            if (!tasksToLaunch.contains(taskSpec.getName())) {
                continue;
            }

            if (!usedResourceSets.contains(taskSpec.getResourceSet().getId())) {
                LOGGER.info("Generating taskInfo to launch for: {}, with resource set: {}",
                        taskSpec.getName(), taskSpec.getResourceSet().getId());
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskInfos.add(getNewTaskInfo(podInstance, taskSpec, targetConfigurationId));
            }
        }

        // Generating TaskInfos to complete Pod footprint.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            if (tasksToLaunch.contains(taskSpec.getName())) {
                continue;
            }

            if (!usedResourceSets.contains(taskSpec.getResourceSet().getId())) {
                LOGGER.info("Generating transient taskInfo to complete pod footprint for: {}, with resource set: {}",
                        taskSpec.getName(), taskSpec.getResourceSet().getId());
                Protos.TaskInfo taskInfo =
                        getNewTaskInfo(podInstance, taskSpec, targetConfigurationId);
                taskInfo = CommonTaskUtils.setTransient(taskInfo.toBuilder()).build();
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskInfos.add(taskInfo);
            }
        }

        return taskInfos;
    }

    private static Protos.TaskInfo getNewTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            UUID targetConfigurationId,
            Collection<Protos.Resource> resources) throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonTaskUtils.emptyTaskId())
                .setSlaveId(CommonTaskUtils.emptyAgentId())
                .addAllResources(resources);

        // create default labels:
        CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        CommonTaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());
        TaskUtils.setGoalState(taskInfoBuilder, taskSpec);
        CommonTaskUtils.setType(taskInfoBuilder, podInstance.getPod().getType());
        CommonTaskUtils.setIndex(taskInfoBuilder, podInstance.getIndex());

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            taskInfoBuilder.getCommandBuilder()
                    .setValue(commandSpec.getValue())
                    .setEnvironment(getTaskEnvironment(podInstance, taskSpec, commandSpec));
        }

        setHealthCheck(taskInfoBuilder, taskSpec);
        setReadinessCheck(taskInfoBuilder, taskSpec);

        return taskInfoBuilder.build();
    }

    private static Protos.TaskInfo getNewTaskInfo(
            PodInstance podInstance, TaskSpec taskSpec, UUID targetConfigurationId) throws InvalidRequirementException {
        return getNewTaskInfo(podInstance, taskSpec, targetConfigurationId, getNewResources(taskSpec));
    }

    private static TaskRequirement getExistingTaskRequirement(
            PodInstance podInstance,
            Protos.TaskInfo taskInfo,
            TaskSpec taskSpec,
            UUID targetConfigurationId) throws InvalidRequirementException {
        List<Protos.Resource> diskResources = new ArrayList<>();
        List<Protos.Resource> otherResources = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (resource.hasDisk()) {
                // Disk resources may not be changed:
                diskResources.add(resource);
            } else {
                otherResources.add(resource);
            }
        }

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .clearExecutor()
                .addAllResources(getUpdatedResources(otherResources, taskSpec))
                .addAllResources(diskResources)
                .setTaskId(CommonTaskUtils.emptyTaskId())
                .setSlaveId(CommonTaskUtils.emptyAgentId());

        // update some labels:
        CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        CommonTaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());
        CommonTaskUtils.clearTransient(taskInfoBuilder);

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            Protos.CommandInfo.Builder commandBuilder = Protos.CommandInfo.newBuilder()
                    .setValue(commandSpec.getValue())
                    .setEnvironment(mergeEnvironments(getTaskEnvironment(
                            podInstance, taskSpec, commandSpec), taskInfo.getCommand().getEnvironment()));
            for (URI uri : commandSpec.getUris()) {
                commandBuilder.addUrisBuilder().setValue(uri.toString());
            }
            // Overwrite any prior CommandInfo:
            taskInfoBuilder.setCommand(commandBuilder);
        }

        setHealthCheck(taskInfoBuilder, taskSpec);
        setReadinessCheck(taskInfoBuilder, taskSpec);

        return new TaskRequirement(taskInfoBuilder.build());
    }

    private static Protos.Environment getTaskEnvironment(
            PodInstance podInstance, TaskSpec taskSpec, CommandSpec commandSpec) {
        Map<String, String> environment = new HashMap<>();

        // Developer-provided or user-provided TASKCFG_* envvars (overrides ServiceSpec)
        environment.putAll(commandSpec.getEnvironment());

        // Default envvars for use by executors/developers:

        // Inject Pod Instance Index
        environment.put(POD_INSTANCE_INDEX_KEY, String.valueOf(podInstance.getIndex()));
        // Inject TASK_NAME as KEY:VALUE
        environment.put(TASK_NAME_KEY, TaskSpec.getInstanceName(podInstance, taskSpec));
        // Inject TASK_NAME as KEY for conditional mustache templating
        environment.put(TaskSpec.getInstanceName(podInstance, taskSpec), "true");

        return CommonTaskUtils.fromMapToEnvironment(environment).build();
    }

    private static Protos.Environment mergeEnvironments(Protos.Environment lhs, Protos.Environment rhs) {
        Map<String, String> lhsVariables = CommonTaskUtils.fromEnvironmentToMap(lhs);
        Map<String, String> rhsVariables = CommonTaskUtils.fromEnvironmentToMap(rhs);
        for (Map.Entry<String, String> entry : rhsVariables.entrySet()) {
            if (!lhsVariables.containsKey(entry.getKey())) {
                lhsVariables.put(entry.getKey(), entry.getValue());
            }
        }

        return CommonTaskUtils.fromMapToEnvironment(lhsVariables).build();
    }

    private static void validateTaskRequirements(List<TaskRequirement> taskRequirements)
            throws InvalidRequirementException {
        if (taskRequirements.isEmpty()) {
            throw new InvalidRequirementException("Failed to generate any TaskRequirements.");
        }

        String taskType = "";
        try {
            taskType = CommonTaskUtils.getType(taskRequirements.get(0).getTaskInfo());
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        for (TaskRequirement taskRequirement : taskRequirements) {
            try {
                String localTaskType = CommonTaskUtils.getType(taskRequirement.getTaskInfo());
                if (!localTaskType.equals(taskType)) {
                    throw new InvalidRequirementException("TaskRequirements must have TaskTypes.");
                }
            } catch (TaskException e) {
                throw new InvalidRequirementException(e);
            }
        }
    }

    private static Collection<Protos.Resource> getUpdatedResources(
            Collection<Protos.Resource> oldResources, TaskSpec taskSpec) throws InvalidRequirementException {

        Map<String, Protos.Resource> oldResourceMap = oldResources.stream()
                .collect(Collectors.toMap(resource -> resource.getName(), resource -> resource));

        List<Protos.Resource> updatedResources = new ArrayList<>();
        for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpec.getName());
            if (oldResource != null) {
                // Update existing resource
                try {
                    updatedResources.add(ResourceUtils.updateResource(oldResource, resourceSpec));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to update Resources with exception: ", e);
                    // On failure to update resources, keep the old resources.
                    updatedResources.add(oldResource);
                }
            } else {
                // Add newly added resource
                updatedResources.add(ResourceUtils.getDesiredResource(resourceSpec));
            }
        }

        return coalesceResources(updatedResources);
    }

    private static Collection<Protos.Resource> getNewResources(TaskSpec taskSpec)
            throws InvalidRequirementException {
        ResourceSet resourceSet = taskSpec.getResourceSet();
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpec resourceSpec : resourceSet.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpec));
        }

        for (VolumeSpec volumeSpec : resourceSet.getVolumes()) {
            switch (volumeSpec.getType()) {
                case ROOT:
                    resources.add(
                            ResourceUtils.getDesiredRootVolume(
                                    volumeSpec.getRole(),
                                    volumeSpec.getPrincipal(),
                                    volumeSpec.getValue().getScalar().getValue(),
                                    volumeSpec.getContainerPath()));
                    break;
                case MOUNT:
                    resources.add(
                            ResourceUtils.getDesiredMountVolume(
                                    volumeSpec.getRole(),
                                    volumeSpec.getPrincipal(),
                                    volumeSpec.getValue().getScalar().getValue(),
                                    volumeSpec.getContainerPath()));
                    break;
                default:
                    LOGGER.error("Encountered unsupported disk type: " + volumeSpec.getType());
            }
        }

        return coalesceResources(resources);
    }

    private static List<Protos.Resource> coalesceResources(Collection<Protos.Resource> resources) {
        List<Protos.Resource> portResources = new ArrayList<>();
        List<Protos.Resource> otherResources = new ArrayList<>();
        for (Protos.Resource r : resources) {
            if (isPortResource(r)) {
                portResources.add(r);
            } else {
                otherResources.add(r);
            }
        }

        if (!portResources.isEmpty()) {
            otherResources.add(coalescePorts(portResources));
        }

        return otherResources;
    }

    private static boolean isPortResource(Protos.Resource resource) {
        return resource.getName().equals(PORTS_RESOURCE_TYPE);
    }

    private static Protos.Resource coalescePorts(List<Protos.Resource> resources) {
        // Within the SDK, each port is handled as its own resource, since they can have extra meta-data attached, but
        // we can't have multiple "ports" resources on a task info, so we combine them here. Since ports are also added
        // back onto TaskInfos during the evaluation stage (since they may be dynamic) we actually just clear the ranges
        // from that resource here to make the bookkeeping easier.
        // TODO(mrb): instead of clearing ports, keep them in OfferRequirement and build up actual TaskInfos elsewhere
        return resources.get(0).toBuilder().clearRanges().build();
    }

    private static Map<String, Protos.Resource> getResourceMap(Collection<Protos.Resource> resources) {
        Map<String, Protos.Resource> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resources) {
            if (!resource.hasDisk()) {
                resourceMap.put(resource.getName(), resource);
            }
        }

        return resourceMap;
    }

    private static Collection<Protos.Resource> getVolumes(Collection<Protos.Resource> resources) {
        List<Protos.Resource> volumes = new ArrayList<>();
        for (Protos.Resource resource : resources) {
            if (resource.hasDisk()) {
                volumes.add(resource);
            }
        }

        return volumes;
    }

    /**
     * Returns the ExecutorInfo of a PodInstance if it is still running so it may be re-used, otherwise
     * it returns a new ExecutorInfo.
     * @param podInstance A PodInstance
     * @return The appropriate ExecutorInfo.
     */
    private Protos.ExecutorInfo getExecutor(PodInstance podInstance) {
        List<Protos.TaskInfo> podTasks = TaskUtils.getPodTasks(podInstance, stateStore);

        for (Protos.TaskInfo taskInfo : podTasks) {
            Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());
            if (taskStatusOptional.isPresent()
                    && taskStatusOptional.get().getState() == Protos.TaskState.TASK_RUNNING) {
                LOGGER.info(
                        "Reusing executor from task '{}': {}",
                        taskInfo.getName(),
                        TextFormat.shortDebugString(taskInfo.getExecutor()));
                return taskInfo.getExecutor();
            }
        }

        LOGGER.info("Creating new executor for pod {}, as no RUNNING tasks were found", podInstance.getName());
        return getNewExecutorInfo(podInstance.getPod());
    }

    private static Protos.ContainerInfo getContainerInfo(ContainerSpec containerSpec) {
        Protos.ContainerInfo.Builder containerInfo = Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (containerSpec.getImageName().isPresent()) {
            containerInfo.getMesosBuilder()
                    .setImage(Protos.Image.newBuilder()
                            .setType(Protos.Image.Type.DOCKER)
                            .setDocker(Protos.Image.Docker.newBuilder()
                                    .setName(containerSpec.getImageName().get())));
        }

        if (!containerSpec.getRLimits().isEmpty()) {
            containerInfo.setRlimitInfo(getRLimitInfo(containerSpec.getRLimits()));
        }

        return containerInfo.build();
    }

    private static Protos.RLimitInfo getRLimitInfo(Collection<RLimit> rlimits) {
        Protos.RLimitInfo.Builder rLimitInfoBuilder = Protos.RLimitInfo.newBuilder();

        for (RLimit rLimit : rlimits) {
            Optional<Long> soft = rLimit.getSoft();
            Optional<Long> hard = rLimit.getHard();
            Protos.RLimitInfo.RLimit.Builder rLimitsBuilder = Protos.RLimitInfo.RLimit.newBuilder()
                    .setType(rLimit.getEnum());

            // RLimit itself validates that both or neither of these are present.
            if (soft.isPresent() && hard.isPresent()) {
                rLimitsBuilder.setSoft(soft.get()).setHard(hard.get());
            }
            rLimitInfoBuilder.addRlimits(rLimitsBuilder);
        }

        return rLimitInfoBuilder.build();
    }

    private static Protos.ExecutorInfo getNewExecutorInfo(PodSpec podSpec) throws IllegalStateException {
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        if (podSpec.getContainer().isPresent()) {
            executorInfoBuilder.setContainer(getContainerInfo(podSpec.getContainer().get()));
        }

        // command and user:

        Protos.CommandInfo.Builder commandInfoBuilder = executorInfoBuilder.getCommandBuilder()
                .setValue("export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib && " +
                        "export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && " +
                        "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && " +
                        "./executor/bin/executor");

        if (podSpec.getUser().isPresent()) {
            commandInfoBuilder.setUser(podSpec.getUser().get());
        }

        // URIs:

        String executorUri = System.getenv(EXECUTOR_URI);
        if (executorUri == null) {
            throw new IllegalStateException("Missing environment variable: " + EXECUTOR_URI);
        }
        commandInfoBuilder.addUrisBuilder().setValue(executorUri);

        String libmesosUri = System.getenv(LIBMESOS_URI);
        if (libmesosUri == null) {
            throw new IllegalStateException("Missing environment variable: " + LIBMESOS_URI);
        }
        commandInfoBuilder.addUrisBuilder().setValue(libmesosUri);

        // Reuse scheduler's JAVA_URI for executors when available:
        String javaUri = System.getenv(JAVA_URI);
        if (javaUri == null) {
            javaUri = DEFAULT_JAVA_URI;
        }
        commandInfoBuilder.addUrisBuilder().setValue(javaUri);

        for (TaskSpec taskSpec : podSpec.getTasks()) {
            if (!taskSpec.getCommand().isPresent()) {
                continue;
            }
            for (URI uri : taskSpec.getCommand().get().getUris()) {
                commandInfoBuilder.addUrisBuilder().setValue(uri.toString());
            }
        }

        return executorInfoBuilder.build();
    }

    private static void setHealthCheck(Protos.TaskInfo.Builder taskInfo, TaskSpec taskSpec) {
        if (!taskSpec.getHealthCheck().isPresent()) {
            LOGGER.debug("No health check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        HealthCheckSpec healthCheckSpec = taskSpec.getHealthCheck().get();
        taskInfo.getHealthCheckBuilder()
                .setDelaySeconds(healthCheckSpec.getDelay())
                .setIntervalSeconds(healthCheckSpec.getInterval())
                .setTimeoutSeconds(healthCheckSpec.getTimeout())
                .setConsecutiveFailures(healthCheckSpec.getMaxConsecutiveFailures())
                .setGracePeriodSeconds(healthCheckSpec.getGracePeriod());

        Protos.CommandInfo.Builder healthCheckCommandBuilder = taskInfo.getHealthCheckBuilder().getCommandBuilder()
                .setValue(healthCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            healthCheckCommandBuilder.setEnvironment(
                    CommonTaskUtils.fromMapToEnvironment(
                            taskSpec.getCommand().get().getEnvironment()));
        }
    }

    private static void setReadinessCheck(Protos.TaskInfo.Builder taskInfoBuilder, TaskSpec taskSpec) {
        if (!taskSpec.getReadinessCheck().isPresent()) {
            LOGGER.debug("No readiness check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        ReadinessCheckSpec readinessCheckSpec = taskSpec.getReadinessCheck().get();
        Protos.HealthCheck.Builder builder = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout())
                .setConsecutiveFailures(0)
                .setGracePeriodSeconds(0);

        Protos.CommandInfo.Builder readinessCheckCommandBuilder = builder.getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            readinessCheckCommandBuilder.setEnvironment(
                    CommonTaskUtils.fromMapToEnvironment(
                            taskSpec.getCommand().get().getEnvironment()));
        }

        CommonTaskUtils.setReadinessCheck(taskInfoBuilder, builder.build());
    }
}
