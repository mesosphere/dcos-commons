package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.config.TaskConfigRouter;
import org.apache.mesos.specification.*;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A default implementation of the OfferRequirementProvider interface.
 */
public class DefaultOfferRequirementProvider implements OfferRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOfferRequirementProvider.class);
    private static final String EXECUTOR_URI = "EXECUTOR_URI";
    private static final String JAVA_HOME = "JAVA_HOME";
    private static final String DEFAULT_JAVA_HOME = "jre1.8.0_91";
    private static final String DEFAULT_JAVA_URI =
            "https://downloads.mesosphere.com/dcos-commons/artifacts/jre-8u91-linux-x64.tar.gz";

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
    public OfferRequirement getNewOfferRequirement(PodInstance podInstance, List<String> tasksToLaunch)
            throws InvalidRequirementException {

        List<Protos.TaskInfo> taskInfos = getNewTaskInfos(podInstance);

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
    public OfferRequirement getExistingOfferRequirement(PodInstance podInstance, List<String> tasksToLaunch)
            throws InvalidRequirementException {

        List<TaskSpec> taskSpecs = podInstance.getPod().getTasks();
        Map<Protos.TaskInfo, TaskSpec> taskMap = new HashMap<>();

        for (TaskSpec taskSpec : taskSpecs) {
            Optional<Protos.TaskInfo> taskInfoOptional =
                    stateStore.fetchTask(TaskSpec.getInstanceName(podInstance, taskSpec));
            if (taskInfoOptional.isPresent()) {
                taskMap.put(taskInfoOptional.get(), taskSpec);
            } else {
                taskMap.put(getNewTaskInfo(podInstance, taskSpec), taskSpec);
            }
        }

        List<TaskRequirement> taskRequirements = new ArrayList<>();
        for (Map.Entry<Protos.TaskInfo, TaskSpec> taskPair : taskMap.entrySet()) {
            taskRequirements.add(getExistingTaskRequirement(taskPair.getKey(), taskPair.getValue()));
        }

        validateTaskRequirements(taskRequirements);

        ExecutorRequirement executorRequirement =
                ExecutorRequirement.create(getNewExecutorInfo(podInstance.getPod()).build());

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskRequirements,
                executorRequirement,
                podInstance.getPod().getPlacementRule());
    }


    private List<Protos.TaskInfo> getNewTaskInfos(PodInstance podInstance) throws InvalidRequirementException {
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            if (taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING)) {
                taskInfos.add(getNewTaskInfo(podInstance, taskSpec));
            }
        }

        return taskInfos;
    }

    private Protos.TaskInfo getNewTaskInfo(PodInstance podInstance, TaskSpec taskSpec)
            throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpec));

        TaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        TaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());

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

    private Protos.Environment getEnvironment(PodInstance podInstance, TaskSpec taskSpec) {
        if (taskSpec.getCommand().isPresent()) {
            Protos.Environment environment =
                    TaskUtils.fromMapToEnvironment(taskSpec.getCommand().get().getEnvironment());

            // Inject Pod Instance Index
            environment = environment.toBuilder()
                    .addVariables(Protos.Environment.Variable.newBuilder()
                            .setName("POD_INSTANCE_INDEX")
                            .setValue(String.valueOf(podInstance.getIndex()))
                            .build())
                    .build();

            return environment;
        }

        return null;
    }

    private TaskRequirement getExistingTaskRequirement(Protos.TaskInfo taskInfo, TaskSpec taskSpec)
            throws InvalidRequirementException {

        String taskType;
        try {
            taskType = TaskUtils.getType(taskInfo);
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        List<Protos.Resource> updatedResources = getUpdatedResources(taskInfo, taskSpec);

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .clearExecutor()
                .addAllResources(updatedResources)
                .addAllResources(getVolumes(taskInfo.getResourcesList()))
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId());

        TaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        TaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());

        if (taskSpec.getCommand().isPresent()) {
            Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskType)
                    .updateEnvironment(CommandUtils.getCommandInfo(taskSpec.getCommand().get()));
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
            taskType = TaskUtils.getType(taskRequirements.get(0).getTaskInfo());
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        for (TaskRequirement taskRequirement : taskRequirements) {
            try {
                String localTaskType = TaskUtils.getType(taskRequirement.getTaskInfo());
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

    private static Iterable<? extends Protos.Resource> getNewResources(TaskSpec taskSpec)
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

        Protos.CommandInfo.Builder commandInfoBuilder = Protos.CommandInfo.newBuilder()
                .setValue("./executor/bin/executor")
                .addUris(executorURI);

        Protos.Environment.Variable.Builder javaHomeVariable =
                Protos.Environment.Variable.newBuilder().setName(JAVA_HOME);
        javaHomeVariable.setValue(DEFAULT_JAVA_HOME);

        Protos.Environment.Builder envBuilder = Protos.Environment.newBuilder()
                .addVariables(javaHomeVariable);
        commandInfoBuilder.setEnvironment(envBuilder);
        commandInfoBuilder.addUris(TaskUtils.uri(DEFAULT_JAVA_URI));

        if (podSpec.getUser().isPresent()) {
            commandInfoBuilder.setUser(podSpec.getUser().get());
        }

        executorInfoBuilder.setCommand(commandInfoBuilder.build());
        return executorInfoBuilder;
    }
}
