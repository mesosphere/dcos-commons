package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.config.TaskConfigRouter;
import org.apache.mesos.specification.*;
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
    private final UUID targetConfigurationId;

    public DefaultOfferRequirementProvider(
            TaskConfigRouter taskConfigRouter, UUID targetConfigurationId) {
        this.taskConfigRouter = taskConfigRouter;
        this.targetConfigurationId = targetConfigurationId;
    }

    @Override
    public OfferRequirement getNewOfferRequirement(PodInstance podInstance) throws InvalidRequirementException {
        List<Protos.TaskInfo> taskInfos = getTaskInfos(podInstance.getPod());
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
    public OfferRequirement getExistingOfferRequirement(
            List<Protos.TaskInfo> taskInfos,
            Optional<Protos.ExecutorInfo> executorInfoOptional,
            PodInstance podInstance) throws InvalidRequirementException {

        List<TaskRequirement> taskRequirements = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(taskInfo, podInstance.getPod());

            if (taskSpecOptional.isPresent()) {
                taskRequirements.add(getExistingTaskRequirement(taskInfo, taskSpecOptional.get()));
            }
        }

        validateTaskRequirements(taskRequirements);

        ExecutorRequirement executorRequirement = null;
        if (executorInfoOptional.isPresent()) {
            executorRequirement = ExecutorRequirement.create(executorInfoOptional.get());
        } else {
            executorRequirement = ExecutorRequirement.create(getNewExecutorInfo(podInstance.getPod()).build());
        }

        return OfferRequirement.create(
                podInstance.getPod().getType(),
                podInstance.getIndex(),
                taskRequirements,
                executorRequirement,
                podInstance.getPod().getPlacementRule());
    }


    private List<Protos.TaskInfo> getTaskInfos(PodSpec podSpec) throws InvalidRequirementException {
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (TaskSpec taskSpec : podSpec.getTasks()) {
            if (taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING)) {
                taskInfos.add(getTaskInfo(taskSpec));
            }
        }

        return taskInfos;
    }

    private Protos.TaskInfo getTaskInfo(TaskSpec taskSpec) throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(taskSpec.getName())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpec));

        TaskUtils.setTargetConfiguration(taskInfoBuilder, targetConfigurationId);
        TaskUtils.setConfigFiles(taskInfoBuilder, taskSpec.getConfigFiles());

        if (taskSpec.getCommand().isPresent()) {
            Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskSpec.getPod().getType())
                    .updateEnvironment(Protos.CommandInfo.newBuilder()
                            .setValue(taskSpec.getCommand().get().getValue()).build());
            taskInfoBuilder.setCommand(updatedCommand);
        }

        if (taskSpec.getContainer().isPresent()) {
            taskInfoBuilder.setContainer(taskSpec.getContainer().get().getContainerInfo());
        }

        if (taskSpec.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(HealthCheckUtils.getHealthCheck(taskSpec.getHealthCheck().get()));
        }

        return taskInfoBuilder.build();
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

        if (taskSpec.getContainer().isPresent()) {
            taskInfoBuilder.setContainer(taskSpec.getContainer().get().getContainerInfo());
        }

        if (taskSpec.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(HealthCheckUtils.getHealthCheck(taskSpec.getHealthCheck().get()));
        }

        return new TaskRequirement(taskInfo);
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

        ResourceSet resourceSet = getResourceSet(taskSpec);
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


    private static Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        if (taskSpecification.getVolumes().size() > 0) {
            for (VolumeSpecification volumeSpecification : taskSpecification.getVolumes()) {
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

    private static Iterable<? extends Protos.Resource> getNewResources(TaskSpec taskSpec)
            throws InvalidRequirementException {
        ResourceSet resourceSet = getResourceSet(taskSpec);
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

    private static ResourceSet getResourceSet(TaskSpec taskSpec) throws InvalidRequirementException {
        Optional<ResourceSet> resourceSetOptional = taskSpec.getPod().getResources().stream()
                .filter(resourceSet -> resourceSet.getId().equals(taskSpec.getResourceSetId()))
                .findFirst();

        if (resourceSetOptional.isPresent()) {
            return resourceSetOptional.get();
        } else {
            throw new InvalidRequirementException("Failed to find ResourceSet for TaskSpec: " + taskSpec);
        }
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
     * Creates an {@link org.apache.mesos.Protos.ExecutorInfo} that drives
     * the {@link org.apache.mesos.executor.CustomExecutor}.
     * @param taskSpecification The {@link TaskSpecification} used to setup relevant environment for the executor.
     * @return The {@link org.apache.mesos.Protos.ExecutorInfo} to run
     * the {@link org.apache.mesos.executor.CustomExecutor}
     * @throws IllegalStateException
     */
    private Protos.ExecutorInfo getNewExecutorInfo(TaskSpecification taskSpecification) throws IllegalStateException {

        Protos.CommandInfo.URI executorURI;
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        String executorStr = System.getenv(EXECUTOR_URI);
        if (executorStr == null) {
            throw new IllegalStateException("Missing environment variable: " + EXECUTOR_URI);
        }
        executorURI = TaskUtils.uri(executorStr);

        Protos.CommandInfo.Builder commandInfoBuilder = Protos.CommandInfo.newBuilder()
                .setValue("./executor/bin/executor")
                .addUris(executorURI);

        if (taskSpecification.getCommand().isPresent()) {
            Protos.CommandInfo taskCommand = taskSpecification.getCommand().get();
            commandInfoBuilder.addAllUris(taskCommand.getUrisList());

            if (taskCommand.hasUser()) {
                commandInfoBuilder.setUser(taskCommand.getUser());
            }
        }

        // some version of the JRE is required to kickstart the executor
        setJREVersion(commandInfoBuilder, taskSpecification);

        executorInfoBuilder.setCommand(commandInfoBuilder.build());
        return executorInfoBuilder.build();
    }

    private Protos.ExecutorInfo.Builder getNewExecutorInfo(PodSpec podSpec) throws IllegalStateException {
        Protos.CommandInfo.URI executorURI;
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

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
        commandInfoBuilder.addUris(TaskUtils.uri(DEFAULT_JAVA_URI));

        if (podSpec.getUser().isPresent()) {
            commandInfoBuilder.setUser(podSpec.getUser().get());
        }

        executorInfoBuilder.setCommand(commandInfoBuilder.build());
        return executorInfoBuilder;
    }

    /**
     * Determines the version of the JRE to use for the custom executor.
     * @param taskSpecification If a specific JRE is specified, it would be included in
     * the env vars of this given {@link TaskSpecification}.
     * @return The string containing the value of the env var indicating where the JRE lives
     */
    private void setJREVersion(Protos.CommandInfo.Builder commandInfoBuilder, TaskSpecification taskSpecification) {

        if (taskSpecification.getCommand().isPresent()) {
            Protos.Environment.Variable.Builder javaHomeVariable =
                    Protos.Environment.Variable.newBuilder().setName(JAVA_HOME);

            Map<String, String> environment =
                    TaskUtils.fromEnvironmentToMap(taskSpecification.getCommand().get().getEnvironment());
            if (environment.containsKey(JAVA_HOME)) {
                javaHomeVariable.setValue(environment.get(JAVA_HOME));
            } else {
                javaHomeVariable.setValue(DEFAULT_JAVA_HOME);
                commandInfoBuilder.addUris(TaskUtils.uri(DEFAULT_JAVA_URI));
            }

            Protos.Environment.Builder environmentBuilder = Protos.Environment.newBuilder()
                    .addVariables(javaHomeVariable);
            commandInfoBuilder.setEnvironment(environmentBuilder);
        }
    }

}
