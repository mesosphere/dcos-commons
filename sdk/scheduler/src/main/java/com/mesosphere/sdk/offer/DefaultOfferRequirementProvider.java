package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
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

    private static final String DEFAULT_JAVA_URI =
            "https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz";

    private static final String CONFIG_TEMPLATE_KEY_FORMAT = "CONFIG_TEMPLATE_%s";
    private static final String CONFIG_TEMPLATE_DOWNLOAD_PATH = "config-templates/";

    private final StateStore stateStore;
    private final String serviceName;
    private final UUID targetConfigurationId;

    /**
     * Creates a new instance which relies on the provided {@link StateStore} for storing known tasks, and which
     * updates tasks which are not tagged with the provided {@code targetConfigurationId}.
     */
    public DefaultOfferRequirementProvider(StateStore stateStore, String serviceName, UUID targetConfigurationId) {
        this.stateStore = stateStore;
        this.serviceName = serviceName;
        this.targetConfigurationId = targetConfigurationId;
    }

    @Override
    public OfferRequirement getNewOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                getNewTaskRequirements(
                        podInstance,
                        podInstanceRequirement.getTasksToLaunch(),
                        podInstanceRequirement.getEnvironment(),
                        serviceName,
                        targetConfigurationId),
                getExecutorRequirement(podInstance, serviceName, targetConfigurationId),
                podInstance.getPod().getPlacementRule());
    }

    private Collection<TaskRequirement> getNewTaskRequirements(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId) throws InvalidRequirementException{
        LOGGER.info("Getting new TaskRequirements for tasks: {}", tasksToLaunch);

        ArrayList<String> usedResourceSets = new ArrayList<>();
        List<TaskRequirement> taskRequirements = new ArrayList<>();

        // Generating TaskRequirements for evaluation.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            if (!tasksToLaunch.contains(taskSpec.getName())) {
                continue;
            }

            if (!usedResourceSets.contains(taskSpec.getResourceSet().getId())) {
                LOGGER.info("Generating taskInfo to launch for: {}, with resource set: {}",
                        taskSpec.getName(), taskSpec.getResourceSet().getId());
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskRequirements.add(getNewTaskRequirement(
                        podInstance, taskSpec, environment, serviceName, targetConfigurationId, false));
            }
        }

        // Generating TaskRequirements to complete Pod footprint.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            if (tasksToLaunch.contains(taskSpec.getName())) {
                continue;
            }

            if (!usedResourceSets.contains(taskSpec.getResourceSet().getId())) {
                LOGGER.info("Generating transient taskInfo to complete pod footprint for: {}, with resource set: {}",
                        taskSpec.getName(), taskSpec.getResourceSet().getId());
                TaskRequirement taskRequirement = getNewTaskRequirement(
                        podInstance, taskSpec, environment, serviceName, targetConfigurationId, true);
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskRequirements.add(taskRequirement);
            }
        }

        return taskRequirements;
    }

    private TaskRequirement getNewTaskRequirement(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId,
            boolean isTransient) throws InvalidRequirementException {
        Protos.TaskInfo taskInfo = getNewTaskInfo(
                podInstance, taskSpec, environment, serviceName, targetConfigurationId);
        if (isTransient) {
            taskInfo = CommonTaskUtils.setTransient(taskInfo.toBuilder()).build();
        }

        Collection<ResourceRequirement> resourceRequirements = getResourceRequirements(taskSpec);

        return new TaskRequirement(taskInfo, resourceRequirements);
    }

    private static Collection<ResourceRequirement> getResourceRequirements(TaskSpec taskSpec) {
        return getResourceRequirements(taskSpec, null);
    }

    private static Collection<ResourceRequirement> getResourceRequirements(
            TaskSpec taskSpec, Collection<Protos.Resource> resources) {
        ResourceSet resourceSet = taskSpec.getResourceSet();

        Map<String, Protos.Resource> resourceMap = resources == null ?
                Collections.emptyMap() :
                resources.stream()
                        .filter(resource -> !resource.hasDisk())
                        .collect(Collectors.toMap(r -> r.getName(), Function.identity()));

        Map<String, Protos.Resource> volumeMap = resources == null ?
                Collections.emptyMap() :
                resources.stream()
                        .filter(resource -> resource.hasDisk())
                        .filter(resource -> resource.getDisk().hasVolume())
                        .collect(Collectors.toMap(
                                r -> r.getDisk().getVolume().getContainerPath(),
                                Function.identity()));

        List<ResourceRequirement> resourceRequirements = new ArrayList<>();

        for (ResourceSpec r : resourceSet.getResources()) {
            resourceRequirements.add(r.getResourceRequirement(resourceMap.get(r.getName())));
        }

        for (VolumeSpec v : resourceSet.getVolumes()) {
            resourceRequirements.add(v.getResourceRequirement(volumeMap.get(v.getContainerPath())));
        }

        return resourceRequirements;
    }

    private ExecutorRequirement getExecutorRequirement(
            PodInstance podInstance,
            String serviceName,
            UUID targetConfigurationId) throws InvalidRequirementException {
        List<Protos.TaskInfo> podTasks = TaskUtils.getPodTasks(podInstance, stateStore);

        for (Protos.TaskInfo taskInfo : podTasks) {
            Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());
            if (taskStatusOptional.isPresent()
                    && taskStatusOptional.get().getState() == Protos.TaskState.TASK_RUNNING) {
                LOGGER.info(
                        "Reusing executor from task '{}': {}",
                        taskInfo.getName(),
                        TextFormat.shortDebugString(taskInfo.getExecutor()));
                return ExecutorRequirement.create(taskInfo.getExecutor());
            }
        }

        LOGGER.info("Creating new executor for pod {}, as no RUNNING tasks were found", podInstance.getName());
        return ExecutorRequirement.create(getNewExecutorInfo(podInstance.getPod(), serviceName, targetConfigurationId));
    }

    @Override
    public OfferRequirement getExistingOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        List<TaskSpec> taskSpecs = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                .collect(Collectors.toList());
        Map<Protos.TaskInfo, TaskSpec> taskMap = new HashMap<>();

        for (TaskSpec taskSpec : taskSpecs) {
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(
                    TaskSpec.getInstanceName(podInstance, taskSpec));
            if (taskInfoOptional.isPresent()) {
                Protos.TaskInfo.Builder builder = taskInfoOptional.get().toBuilder();
                extendEnv(builder.getCommandBuilder(), podInstanceRequirement.getEnvironment());
                taskMap.put(builder.build(), taskSpec);
            } else {
                Protos.TaskInfo taskInfo = getNewTaskInfo(
                        podInstance,
                        taskSpec,
                        podInstanceRequirement.getEnvironment(),
                        serviceName,
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
            taskRequirements.add(getExistingTaskRequirement(
                    podInstance, e.getKey(), e.getValue(), serviceName, targetConfigurationId));
        }
        validateTaskRequirements(taskRequirements);

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskRequirements,
                ExecutorRequirement.create(getExecutor(podInstance, serviceName, targetConfigurationId)),
                podInstance.getPod().getPlacementRule());
    }

    private static Protos.TaskInfo getNewTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId,
            Collection<Protos.Resource> resources) throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonTaskUtils.emptyTaskId())
                .setSlaveId(CommonTaskUtils.emptyAgentId())
                .addAllResources(resources);

        // create default labels:
        CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        TaskUtils.setGoalState(taskInfoBuilder, taskSpec);
        CommonTaskUtils.setType(taskInfoBuilder, podInstance.getPod().getType());
        CommonTaskUtils.setIndex(taskInfoBuilder, podInstance.getIndex());

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            taskInfoBuilder.getCommandBuilder()
                    .setValue(commandSpec.getValue())
                    .setEnvironment(getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
            setBootstrapConfigFileEnv(taskInfoBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskInfoBuilder.getCommandBuilder(), environment);
        }

        setHealthCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());
        setReadinessCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());

        return taskInfoBuilder.build();
    }

    private static Protos.TaskInfo getNewTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId) throws InvalidRequirementException {
        return getNewTaskInfo(
                podInstance, taskSpec, environment, serviceName, targetConfigurationId, getNewResources(taskSpec));
    }

    private static TaskRequirement getExistingTaskRequirement(
            PodInstance podInstance,
            Protos.TaskInfo taskInfo,
            TaskSpec taskSpec,
            String serviceName,
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

        // update some data:
        CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        CommonTaskUtils.clearTransient(taskInfoBuilder);

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            Protos.CommandInfo.Builder commandBuilder = Protos.CommandInfo.newBuilder()
                    .setValue(commandSpec.getValue())
                    .setEnvironment(mergeEnvironments(
                            getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec),
                            taskInfo.getCommand().getEnvironment()));
            setBootstrapConfigFileEnv(commandBuilder, taskSpec);
            // Overwrite any prior CommandInfo:
            taskInfoBuilder.setCommand(commandBuilder);
        }

        setHealthCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());
        setReadinessCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());

        return new TaskRequirement(
                taskInfoBuilder.build(), getResourceRequirements(taskSpec, taskInfo.getResourcesList()));
    }

    private static void setBootstrapConfigFileEnv(
            CommandInfo.Builder commandInfoBuilder, TaskSpec taskSpec) {
        if (taskSpec.getConfigFiles() == null) {
            return;
        }
        for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
            // For use by bootstrap process: an environment variable pointing to (comma-separated):
            // a. where the template file was downloaded (by the mesos fetcher)
            // b. where the rendered result should go
            commandInfoBuilder.getEnvironmentBuilder().addVariablesBuilder()
                    .setName(String.format(CONFIG_TEMPLATE_KEY_FORMAT, TaskUtils.toEnvName(config.getName())))
                    .setValue(String.format("%s,%s", getConfigTemplateDownloadPath(config), config.getRelativePath()));
        }
    }

    private static void extendEnv(CommandInfo.Builder builder, Map<String, String> environment) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            builder.getEnvironmentBuilder().addVariablesBuilder().setName(entry.getKey()).setValue(entry.getValue());
        }
    }

    private static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
    }

    private static Protos.Environment getTaskEnvironment(
            String serviceName, PodInstance podInstance, TaskSpec taskSpec, CommandSpec commandSpec) {
        Map<String, String> environment = new HashMap<>();

        // Developer-provided or user-provided TASKCFG_* envvars (overrides ServiceSpec)
        environment.putAll(commandSpec.getEnvironment());

        // Default envvars for use by executors/developers:

        // Inject Pod Instance Index
        environment.put(POD_INSTANCE_INDEX_KEY, String.valueOf(podInstance.getIndex()));
        // Inject Framework Name
        environment.put(FRAMEWORK_NAME_KEY, serviceName);
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
                updatedResources.add(ResourceUtils.getExpectedResource(resourceSpec));
            }
        }

        return coalesceResources(updatedResources);
    }

    private static Collection<Protos.Resource> getNewResources(TaskSpec taskSpec)
            throws InvalidRequirementException {
        ResourceSet resourceSet = taskSpec.getResourceSet();
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpec resourceSpec : resourceSet.getResources()) {
            resources.add(ResourceUtils.getExpectedResource(resourceSpec));
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

    /**
     * Returns the ExecutorInfo of a PodInstance if it is still running so it may be re-used, otherwise
     * it returns a new ExecutorInfo.
     * @param podInstance A PodInstance
     * @return The appropriate ExecutorInfo.
     */
    private Protos.ExecutorInfo getExecutor(
            PodInstance podInstance, String serviceName, UUID targetConfigurationId) {
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
        return getNewExecutorInfo(podInstance.getPod(), serviceName, targetConfigurationId);
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

        if (!containerSpec.getNetworks().isEmpty()) {
            containerInfo.addAllNetworkInfos(
                    containerSpec.getNetworks().stream().map(n -> getNetworkInfo(n)).collect(Collectors.toList()));
        }

        if (!containerSpec.getRLimits().isEmpty()) {
            containerInfo.setRlimitInfo(getRLimitInfo(containerSpec.getRLimits()));
        }

        return containerInfo.build();
    }

    private static Protos.NetworkInfo getNetworkInfo(NetworkSpec networkSpec) {
        return Protos.NetworkInfo.newBuilder().setName(networkSpec.getName()).build();
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

    private static Protos.ExecutorInfo getNewExecutorInfo(
            PodSpec podSpec, String serviceName, UUID targetConfigurationId) throws IllegalStateException {
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        if (podSpec.getContainer().isPresent()) {
            executorInfoBuilder.setContainer(getContainerInfo(podSpec.getContainer().get()));
        }

        // command and user:

        Protos.CommandInfo.Builder executorCommandBuilder = executorInfoBuilder.getCommandBuilder().setValue(
                "export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib:$LD_LIBRARY_PATH && " +
                "export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && " +
                "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && " +
                "./executor/bin/executor");

        if (podSpec.getUser().isPresent()) {
            executorCommandBuilder.setUser(podSpec.getUser().get());
        }

        // URIs:

        // Required in scheduler env.
        String executorUri = System.getenv(EXECUTOR_URI);
        if (executorUri == null) {
            throw new IllegalStateException("Missing required environment variable: " + EXECUTOR_URI);
        }
        executorCommandBuilder.addUrisBuilder().setValue(executorUri);

        // Required in scheduler env.
        String libmesosUri = System.getenv(LIBMESOS_URI);
        if (libmesosUri == null) {
            throw new IllegalStateException("Missing required environment variable: " + LIBMESOS_URI);
        }
        executorCommandBuilder.addUrisBuilder().setValue(libmesosUri);

        // Reuse scheduler's JAVA_URI for executors when available, or fall back to default.
        String javaUri = System.getenv(JAVA_URI);
        if (javaUri == null) {
            javaUri = DEFAULT_JAVA_URI;
        }
        executorCommandBuilder.addUrisBuilder().setValue(javaUri);

        // Any URIs defined in PodSpec itself.
        for (URI uri : podSpec.getUris()) {
            executorCommandBuilder.addUrisBuilder().setValue(uri.toString());
        }

        // Finally any URIs for config templates defined in TaskSpecs.
        for (TaskSpec taskSpec : podSpec.getTasks()) {
            for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                executorCommandBuilder.addUrisBuilder()
                        .setValue(ArtifactResource.getTemplateUrl(
                                serviceName,
                                targetConfigurationId,
                                podSpec.getType(),
                                taskSpec.getName(),
                                config.getName()))
                        .setOutputFile(getConfigTemplateDownloadPath(config))
                        .setExtract(false);
            }
        }

        return executorInfoBuilder.build();
    }

    private static void setHealthCheck(
            Protos.TaskInfo.Builder taskInfo,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec,
            CommandSpec commandSpec) {
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
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }
    }

    private static void setReadinessCheck(
            Protos.TaskInfo.Builder taskInfoBuilder,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec,
            CommandSpec commandSpec) {
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
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }

        CommonTaskUtils.setReadinessCheck(taskInfoBuilder, builder.build());
    }
}
