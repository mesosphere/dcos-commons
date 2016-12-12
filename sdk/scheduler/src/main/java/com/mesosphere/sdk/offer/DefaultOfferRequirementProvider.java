package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.TaskConfigRouter;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A default implementation of the OfferRequirementProvider interface.
 */
public class DefaultOfferRequirementProvider implements OfferRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOfferRequirementProvider.class);
    private static final String EXECUTOR_URI = "EXECUTOR_URI";
    private static final String LIBMESOS_URI = "LIBMESOS_URI";
    private static final String JAVA_URI = "JAVA_URI";
    private static final String DEFAULT_JAVA_URI = "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz";

    private static final String JAVA_HOME = "JAVA_HOME";
    private static final String POD_INSTANCE_INDEX_KEY = "POD_INSTANCE_INDEX";

    private final TaskConfigRouter taskConfigRouter;
    private final StateStore stateStore;
    private final UUID targetConfigurationId;

    public DefaultOfferRequirementProvider(
            TaskConfigRouter taskConfigRouter,
            StateStore stateStore,
            UUID targetConfigurationId) {
        this.taskConfigRouter = taskConfigRouter;
        this.stateStore = stateStore;
        this.targetConfigurationId = targetConfigurationId;
    }

    @Override
    public OfferRequirement getNewOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException {
        LOGGER.info("Generating new OfferRequirement for Pod: {}, and Tasks: {}",
                podInstance.getName(), tasksToLaunch);

        List<Protos.TaskInfo> taskInfos = getNewTaskInfos(podInstance, tasksToLaunch);

        Protos.ExecutorInfo.Builder execBuilder = getNewExecutorInfo(podInstance.getPod());
        Protos.CommandInfo.Builder execCmdBuilder = execBuilder.getCommand().toBuilder();

        podInstance.getPod().getTasks()
                .forEach(taskSpec -> execCmdBuilder.addAllUris(CommandUtils.getUris(taskSpec.getCommand().get())));

        Protos.ExecutorInfo executorInfo = execBuilder.setCommand(execCmdBuilder).build();

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskInfos,
                Optional.of(executorInfo),
                podInstance.getPod().getPlacementRule());
    }

    @Override
    public OfferRequirement getExistingOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException {
        LOGGER.info("Generating existing OfferRequirement for Pod: {}, and Tasks: {}",
                podInstance.getName(), tasksToLaunch);

        List<TaskSpec> taskSpecs = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
                .collect(Collectors.toList());
        Map<Protos.TaskInfo, TaskSpec> taskMap = new HashMap<>();

        for (TaskSpec taskSpec : taskSpecs) {
            Optional<Protos.TaskInfo> taskInfoOptional =
                    stateStore.fetchTask(TaskSpec.getInstanceName(podInstance, taskSpec));
            if (taskInfoOptional.isPresent()) {
                taskMap.put(taskInfoOptional.get(), taskSpec);
            } else {
                Protos.TaskInfo taskInfo = getNewTaskInfo(
                        podInstance,
                        taskSpec,
                        StateStoreUtils.getResources(
                                stateStore,
                                podInstance,
                                taskSpec));
                LOGGER.info("Generated new TaskInfo: {}", TextFormat.shortDebugString(taskInfo));
                taskMap.put(taskInfo, taskSpec);
            }
        }

        if (taskMap.size() == 0) {
            LOGGER.warn("Attempting to get existing OfferRequirement generated 0 tasks.");
        }

        List<TaskRequirement> taskRequirements = new ArrayList<>();
        for (Map.Entry<Protos.TaskInfo, TaskSpec> taskPair : taskMap.entrySet()) {
            taskRequirements.add(getExistingTaskRequirement(taskPair.getKey(), taskPair.getValue(), podInstance));
        }

        validateTaskRequirements(taskRequirements);

        Protos.ExecutorInfo.Builder execBuilder = getNewExecutorInfo(podInstance.getPod());
        Protos.CommandInfo.Builder execCmdBuilder = execBuilder.getCommand().toBuilder();

        podInstance.getPod().getTasks()
                .forEach(taskSpec -> execCmdBuilder.addAllUris(CommandUtils.getUris(taskSpec.getCommand().get())));

        Protos.ExecutorInfo executorInfo = execBuilder.setCommand(execCmdBuilder).build();

        ExecutorRequirement executorRequirement =
                ExecutorRequirement.create(executorInfo);

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskRequirements,
                executorRequirement,
                podInstance.getPod().getPlacementRule());
    }


    private List<Protos.TaskInfo> getNewTaskInfos(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) throws InvalidRequirementException {

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
                Protos.TaskInfo taskInfo = getNewTaskInfo(podInstance, taskSpec);
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskInfos.add(taskInfo);
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
                Protos.TaskInfo taskInfo = getNewTaskInfo(podInstance, taskSpec);
                taskInfo = CommonTaskUtils.setTransient(taskInfo);
                usedResourceSets.add(taskSpec.getResourceSet().getId());
                taskInfos.add(taskInfo);
            }
        }

        return taskInfos;
    }

    private Protos.TaskInfo getNewTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Collection<Protos.Resource> resources) throws InvalidRequirementException {

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonTaskUtils.emptyTaskId())
                .setSlaveId(CommonTaskUtils.emptyAgentId())
                .addAllResources(resources);

        taskInfoBuilder = CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        taskInfoBuilder = CommonTaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());
        taskInfoBuilder = TaskUtils.setGoalState(taskInfoBuilder, taskSpec);
        taskInfoBuilder = CommonTaskUtils.setType(taskInfoBuilder, podInstance.getPod().getType());
        taskInfoBuilder = CommonTaskUtils.setIndex(taskInfoBuilder, podInstance.getIndex());

        if (taskSpec.getCommand().isPresent()) {
            final CommandSpec commandSpec = taskSpec.getCommand().get();
            Protos.Environment environment = getEnvironment(podInstance, taskSpec);

            final Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskSpec.getType())
                    .updateEnvironment(Protos.CommandInfo.newBuilder()
                            .setValue(commandSpec.getValue())
                            .setEnvironment(environment)
                            .build());
            taskInfoBuilder.setCommand(updatedCommand);
        }

        if (taskSpec.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(HealthCheckUtils.getHealthCheck(taskSpec));
        }

        return taskInfoBuilder.build();
    }


    private Protos.TaskInfo getNewTaskInfo(PodInstance podInstance, TaskSpec taskSpec)
            throws InvalidRequirementException {
        return getNewTaskInfo(podInstance, taskSpec, getNewResources(taskSpec));
    }


    private Protos.Environment getEnvironment(PodInstance podInstance, TaskSpec taskSpec) {
        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();

            Protos.Environment environment;
            if (commandSpec.getEnvironment() != null) {
                environment = CommonTaskUtils.fromMapToEnvironment(taskSpec.getCommand().get().getEnvironment());
            } else {
                environment = Protos.Environment.getDefaultInstance();
            }

            Protos.Environment.Builder envBuilder = environment.toBuilder();
            // Inject Pod Instance Index
            envBuilder.addVariablesBuilder()
                    .setName(POD_INSTANCE_INDEX_KEY)
                    .setValue(String.valueOf(podInstance.getIndex()));
            // Inject TASK_NAME as KEY:VALUE
            envBuilder.addVariablesBuilder()
                    .setName(CommonTaskUtils.TASK_NAME_KEY)
                    .setValue(TaskSpec.getInstanceName(podInstance, taskSpec));
            // Inject TASK_NAME as KEY for conditional mustache templating
            envBuilder.addVariablesBuilder()
                    .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                    .setValue("true")
                    .build();

            return envBuilder.build();
        }

        return null;
    }

    private TaskRequirement getExistingTaskRequirement(
            Protos.TaskInfo taskInfo,
            TaskSpec taskSpec,
            PodInstance podInstance) throws InvalidRequirementException {

        String taskType;
        try {
            taskType = CommonTaskUtils.getType(taskInfo);
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        List<Protos.Resource> updatedResources = getUpdatedResources(taskInfo, taskSpec);

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .clearExecutor()
                .addAllResources(updatedResources)
                .addAllResources(getVolumes(taskInfo.getResourcesList()))
                .setTaskId(CommonTaskUtils.emptyTaskId())
                .setSlaveId(CommonTaskUtils.emptyAgentId());

        CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        CommonTaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());

        if (taskSpec.getCommand().isPresent()) {
            Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskType)
                    .updateEnvironment(CommandUtils.getCommandInfo(taskSpec.getCommand().get()));
            updatedCommand =
                    CommandUtils.addEnvVar(
                            updatedCommand,
                            POD_INSTANCE_INDEX_KEY,
                            String.valueOf(podInstance.getIndex()));
            updatedCommand =
                    CommandUtils.addEnvVar(
                            updatedCommand,
                            CommonTaskUtils.TASK_NAME_KEY,
                            TaskSpec.getInstanceName(podInstance, taskSpec));
            updatedCommand =
                    CommandUtils.addEnvVar(
                            updatedCommand,
                            TaskSpec.getInstanceName(podInstance, taskSpec),
                            "true");
            taskInfoBuilder.setCommand(updatedCommand);
        }

        if (taskSpec.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(HealthCheckUtils.getHealthCheck(taskSpec));
        }

        return new TaskRequirement(taskInfoBuilder.build());
    }

    private void validateTaskRequirements(List<TaskRequirement> taskRequirements) throws InvalidRequirementException {
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

    private List<Protos.Resource> getUpdatedResources(Protos.TaskInfo taskInfo, TaskSpec taskSpec)
            throws InvalidRequirementException {

        Map<String, Protos.Resource> oldResourceMap = getResourceMap(taskInfo.getResourcesList());

        ResourceSet resourceSet = taskSpec.getResourceSet();
        List<Protos.Resource> updatedResources = new ArrayList<>();
        for (ResourceSpecification resourceSpecification : resourceSet.getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpecification.getName());
            if (oldResource != null) {
                try {
                    updatedResources.add(ResourceUtils.updateResource(oldResource, resourceSpecification));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to update Resources with exception: ", e);
                    // On failure to update resources keep the old resources.
                    updatedResources.add(oldResource);
                }
            } else {
                updatedResources.add(ResourceUtils.getDesiredResource(resourceSpecification));
            }
        }

        return updatedResources;
    }

    private static Collection<Protos.Resource> getNewResources(TaskSpec taskSpec)
            throws InvalidRequirementException {
        ResourceSet resourceSet = taskSpec.getResourceSet();
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : resourceSet.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        if (resourceSet.getVolumes().size() > 0) {
            for (VolumeSpecification volumeSpecification : resourceSet.getVolumes()) {
                switch (volumeSpecification.getType()) {
                    case ROOT:
                        resources.add(
                                ResourceUtils.getDesiredRootVolume(
                                        volumeSpecification.getRole(),
                                        volumeSpecification.getPrincipal(),
                                        volumeSpecification.getValue().getScalar().getValue(),
                                        volumeSpecification.getContainerPath()));
                        break;
                    case MOUNT:
                        resources.add(
                                ResourceUtils.getDesiredMountVolume(
                                        volumeSpecification.getRole(),
                                        volumeSpecification.getPrincipal(),
                                        volumeSpecification.getValue().getScalar().getValue(),
                                        volumeSpecification.getContainerPath()));
                        break;
                    default:
                        LOGGER.error("Encountered unsupported disk type: " + volumeSpecification.getType());
                }
            }
        }

        return resources;
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

    private Protos.ExecutorInfo.Builder getNewExecutorInfo(PodSpec podSpec) throws IllegalStateException {
        Protos.CommandInfo.URI executorURI;
        Protos.CommandInfo.URI libmesosURI;

        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        if (podSpec.getContainer().isPresent()) {
            executorInfoBuilder.setContainer(
                    Protos.ContainerInfo.newBuilder()
                            .setType(Protos.ContainerInfo.Type.MESOS)
                            .setDocker(Protos.ContainerInfo.DockerInfo.newBuilder()
                                .setImage(podSpec.getContainer().get().getImageName()))
            );
        }

        String executorStr = System.getenv(EXECUTOR_URI);
        if (executorStr == null) {
            throw new IllegalStateException("Missing environment variable: " + EXECUTOR_URI);
        }
        executorURI = TaskUtils.uri(executorStr);

        String libmesosStr = System.getenv(LIBMESOS_URI);
        if (libmesosStr == null) {
            throw new IllegalStateException("Missing environment variable: " + LIBMESOS_URI);
        }
        libmesosURI = TaskUtils.uri(libmesosStr);

        Protos.CommandInfo.Builder commandInfoBuilder = Protos.CommandInfo.newBuilder()
                .setValue("export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib && " +
                        "export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && " +
                        "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && " +
                        "./executor/bin/executor")
                .addUris(executorURI)
                .addUris(libmesosURI);

        String javaUri = System.getenv(JAVA_URI);
        javaUri = javaUri == null ? DEFAULT_JAVA_URI : javaUri;
        commandInfoBuilder.addUris(TaskUtils.uri(javaUri));

        if (podSpec.getUser().isPresent()) {
            commandInfoBuilder.setUser(podSpec.getUser().get());
        }

        executorInfoBuilder.setCommand(commandInfoBuilder.build());
        return executorInfoBuilder;
    }
}
