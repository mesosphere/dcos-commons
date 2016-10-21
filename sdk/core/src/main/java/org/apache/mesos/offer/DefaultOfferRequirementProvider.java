package org.apache.mesos.offer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.config.DefaultTaskConfigRouter;
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
    private final String JAVA_HOME = "JAVA_HOME";
    private final String DEFAULT_JAVA_HOME = "jre1.8.0_91";
    private final String DEFAULT_JAVA_URI = "https://downloads.mesosphere.com/hdfs/assets/jre-8u91-linux-x64.tar.gz";

    private final TaskConfigRouter taskConfigRouter;

    public DefaultOfferRequirementProvider() {
        this(new DefaultTaskConfigRouter());
    }

    public DefaultOfferRequirementProvider(TaskConfigRouter taskConfigRouter) {
        this.taskConfigRouter = taskConfigRouter;
    }

    @Override
    public OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpecification));

        TaskUtils.setConfigFiles(taskInfoBuilder, taskSpecification.getConfigFiles());

        if (taskSpecification.getCommand().isPresent()) {
            Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskSpecification.getType())
                    .updateEnvironment(taskSpecification.getCommand().get());
            taskInfoBuilder.setCommand(updatedCommand);
        }

        if (taskSpecification.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(taskSpecification.getHealthCheck().get());
        }

        if (taskSpecification.getContainer().isPresent()) {
            taskInfoBuilder.setContainer(taskSpecification.getContainer().get());
        }

        return new OfferRequirement(
                taskSpecification.getType(),
                Arrays.asList(taskInfoBuilder.build()),
                Optional.of(getExecutorInfo(taskSpecification)),
                taskSpecification.getPlacement());
    }


    @Override
    public OfferRequirement getExistingOfferRequirement(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        validateVolumes(taskInfo, taskSpecification);
        Map<String, Protos.Resource> oldResourceMap = getResourceMap(taskInfo.getResourcesList());

        List<Protos.Resource> updatedResources = new ArrayList<>();
        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
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

        String taskType;
        try {
            taskType = TaskUtils.getTaskType(taskInfo);
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .clearExecutor()
                .addAllResources(updatedResources)
                .addAllResources(getVolumes(taskInfo.getResourcesList()))
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId());

        TaskUtils.setConfigFiles(taskInfoBuilder, taskSpecification.getConfigFiles());

        if (taskSpecification.getCommand().isPresent()) {
            Protos.CommandInfo updatedCommand = taskConfigRouter.getConfig(taskType)
                    .updateEnvironment(taskSpecification.getCommand().get());
            taskInfoBuilder.setCommand(updatedCommand);
        }

        if (taskSpecification.getHealthCheck().isPresent()) {
            taskInfoBuilder.setHealthCheck(taskSpecification.getHealthCheck().get());
        }

        if (taskSpecification.getContainer().isPresent()) {
            taskInfoBuilder.setContainer(taskSpecification.getContainer().get());
        }

        try {
            return new OfferRequirement(
                    TaskUtils.getTaskType(taskInfo),
                    Arrays.asList(taskInfoBuilder.build()),
                    Optional.of(getExecutorInfo(taskSpecification)),
                    taskSpecification.getPlacement());
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }
    }

    private static void validateVolumes(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        try {
            TaskSpecification oldTaskSpecification = DefaultTaskSpecification.create(taskInfo);
            Collection<VolumeSpecification> oldVolumes = oldTaskSpecification.getVolumes();
            Collection<VolumeSpecification> newVolumes = taskSpecification.getVolumes();

            if (oldVolumes.size() > 0 && newVolumes.size() > 0) {
                if (!CollectionUtils.isEqualCollection(oldVolumes, newVolumes)){
                    throw new InvalidRequirementException(
                            String.format("Volumes must be equal.  Old volumes: '%s', New volumes: '%s'",
                                    oldTaskSpecification.getVolumes(), taskSpecification.getVolumes()));
                }
            } else if (!(oldVolumes.size() == 0 && newVolumes.size() == 0)) {
                throw new InvalidRequirementException(
                        String.format("Volumes must be equal.  Old volumes: '%s', New volumes: '%s'",
                                oldTaskSpecification.getVolumes(), taskSpecification.getVolumes()));
            }
        } catch (InvalidTaskSpecificationException | TaskException e) {
            throw new InvalidRequirementException(e);
        }
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
     * Creates an {@link Protos.org.apache.mesos.Protos.ExecutorInfo} that drives
     * the {@link org.apache.mesos.executor.CustomExecutor}.
     * @param taskSpecification The {@link TaskSpecification} used to setup relevant environment for the executor.
     * @return The {@link org.apache.mesos.Protos.ExecutorInfo} to run
     * the {@link org.apache.mesos.executor.CustomExecutor}
     * @throws IllegalStateException
     */
    private Protos.ExecutorInfo getExecutorInfo(TaskSpecification taskSpecification) throws IllegalStateException {

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
                .addUris(executorURI)
                .addAllUris(taskSpecification.getCommand().getUrisList());

        // some version of the JRE is required to kickstart the executor
        setJREVersion(commandInfoBuilder, taskSpecification);

        executorInfoBuilder.setCommand(commandInfoBuilder.build());
        return executorInfoBuilder.build();
    }

    /**
     * Determines the version of the JRE to use for the custom executor.
     * @param taskSpecification If a specific JRE is specified, it would be included in
     * the env vars of this given {@link TaskSpecification}.
     * @return The string containing the value of the env var indicating where the JRE lives
     */
    private void setJREVersion(Protos.CommandInfo.Builder commandInfoBuilder, TaskSpecification taskSpecification) {
        Protos.Environment.Variable.Builder javaHomeVariable =
                Protos.Environment.Variable.newBuilder().setName(JAVA_HOME);

        Map<String, String> environment =
                TaskUtils.fromEnvironmentToMap(taskSpecification.getCommand().getEnvironment());
        if (environment.containsKey(JAVA_HOME)) {
            javaHomeVariable.setValue(environment.get(JAVA_HOME));
        } else {
            javaHomeVariable.setValue(DEFAULT_JAVA_HOME);
            commandInfoBuilder.addUris(TaskUtils.uri(DEFAULT_JAVA_URI));
        }

        Protos.Environment.Builder environmentBuilder = Protos.Environment.newBuilder().addVariables(javaHomeVariable);
        commandInfoBuilder.setEnvironment(environmentBuilder);
    }
}
