package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */
public class TaskUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskUtils.class);

    private TaskUtils() {
        // do not instantiate
    }

    /**
     * Returns the {@link TaskSpec} in the provided {@link com.mesosphere.sdk.specification.DefaultServiceSpec}
     * which matches the provided {@link TaskInfo}, or {@code null} if no match could be found.
     */
    public static PodSpec getPodSpec(ServiceSpec serviceSpec, TaskInfo taskInfo) throws TaskException {
        String podType = CommonTaskUtils.getType(taskInfo);

        for (PodSpec podSpec : serviceSpec.getPods()) {
            if (podSpec.getType().equals(podType)) {
                return podSpec;
            }
        }

        return null;
    }

    public static List<String> getTaskNames(PodInstance podInstance) {
        return podInstance.getPod().getTasks().stream()
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());
    }

    public static List<TaskInfo> getPodTasks(PodInstance podInstance, StateStore stateStore) {
        return stateStore.fetchTasks().stream()
                .filter(taskInfo -> {
                    try {
                        return CommonTaskUtils.getType(taskInfo).equals(podInstance.getName());
                    } catch (TaskException e) {
                        LOGGER.error("Encountered ");
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public static List<TaskInfo> getTaskInfosShouldBeRunning(PodInstance podInstance, StateStore stateStore) {
        List<TaskInfo> podTasks = getPodTasks(podInstance, stateStore);

        List<TaskInfo> tasksShouldBeRunning = new ArrayList<>();
        for (TaskInfo taskInfo : podTasks) {
            Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(taskInfo, podInstance);

            if (taskSpecOptional.isPresent() && taskSpecOptional.get().getGoal().equals(TaskSpec.GoalState.RUNNING)) {
                tasksShouldBeRunning.add(taskInfo);
            }
        }

        return tasksShouldBeRunning;
    }

    private static Optional<TaskSpec> getTaskSpec(TaskInfo taskInfo, PodInstance podInstance) {
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            String taskName = TaskSpec.getInstanceName(podInstance, taskSpec);
            if (taskInfo.getName().equals(taskName)) {
                return Optional.of(taskSpec);
            }
        }

        return Optional.empty();
    }

    public static Optional<ExecutorInfo> getExecutor(PodInstance podInstance, StateStore stateStore) {
        List<TaskInfo> shouldBeRunningTasks = getTaskInfosShouldBeRunning(podInstance, stateStore);

        for (TaskInfo taskInfo : shouldBeRunningTasks) {
            Optional<TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());
            if (taskStatusOptional.isPresent()
                    && taskStatusOptional.get().getState() == TaskState.TASK_RUNNING) {
                LOGGER.info("Reusing executor: ", taskInfo.getExecutor());
                return Optional.of(taskInfo.getExecutor());
            }
        }

        LOGGER.info("No running executor found.");
        return Optional.empty();
    }



    public static boolean areDifferent(TaskSpec oldTaskSpec, TaskSpec newTaskSpec) {

        // Names

        String oldTaskName = oldTaskSpec.getName();
        String newTaskName = newTaskSpec.getName();
        if (!Objects.equals(oldTaskName, newTaskName)) {
            LOGGER.info("Task names '{}' and '{}' are different.", oldTaskName, newTaskName);
            return true;
        }

        // CommandInfos

        Optional<CommandSpec> oldCommand = oldTaskSpec.getCommand();
        Optional<CommandSpec> newCommand = newTaskSpec.getCommand();
        if (!Objects.equals(oldCommand, newCommand)) {
            LOGGER.info("Task commands '{}' and '{}' are different.", oldCommand, newCommand);
            return true;
        }

        // Health checks

        Optional<HealthCheckSpec> oldHealthCheck = oldTaskSpec.getHealthCheck();
        Optional<HealthCheckSpec> newHealthCheck = newTaskSpec.getHealthCheck();
        if (!Objects.equals(oldHealthCheck, newHealthCheck)) {
            LOGGER.info("Task healthchecks '{}' and '{}' are different.", oldHealthCheck, newHealthCheck);
            return true;
        }

        // Resources (custom comparison)

        Map<String, ResourceSpecification> oldResourceMap =
                getResourceSpecMap(oldTaskSpec.getResourceSet().getResources());
        Map<String, ResourceSpecification> newResourceMap =
                getResourceSpecMap(newTaskSpec.getResourceSet().getResources());

        if (oldResourceMap.size() != newResourceMap.size()) {
            LOGGER.info("Resource lengths are different for old resources: '{}' and new resources: '{}'",
                    oldResourceMap, newResourceMap);
            return true;
        }

        for (Map.Entry<String, ResourceSpecification> newEntry : newResourceMap.entrySet()) {
            String resourceName = newEntry.getKey();
            LOGGER.info("Checking resource difference for: {}", resourceName);
            ResourceSpecification oldResourceSpec = oldResourceMap.get(resourceName);
            if (oldResourceSpec == null) {
                LOGGER.info("Resource not found: {}", resourceName);
                return true;
            } else if (ResourceUtils.areDifferent(oldResourceSpec, newEntry.getValue())) {
                LOGGER.info("Resources are different.");
                return true;
            }
        }

        // Volumes (custom comparison)

        if (!volumesEqual(oldTaskSpec, newTaskSpec)) {
            LOGGER.info("Task volumes '{}' and '{}' are different.",
                    oldTaskSpec.getResourceSet().getVolumes(),
                    newTaskSpec.getResourceSet().getVolumes());
            return true;
        }

        // Config files

        Map<String, String> oldConfigMap = getConfigTemplateMap(oldTaskSpec.getConfigFiles());
        Map<String, String> newConfigMap = getConfigTemplateMap(newTaskSpec.getConfigFiles());
        if (!Objects.equals(oldConfigMap, newConfigMap)) {
            LOGGER.info("Config templates '{}' and '{}' are different.", oldConfigMap, newConfigMap);
            return true;
        }

        return false;
    }

    /**
     * Utility method for checking if volumes changed between the two provided
     * {@link TaskSpec}s.
     *
     * @return whether the volume lists are equal
     */
    public static boolean volumesEqual(TaskSpec first, TaskSpec second) {
        return CollectionUtils.isEqualCollection(
                first.getResourceSet().getVolumes(),
                second.getResourceSet().getVolumes());
    }

    /**
     * Returns a name=>resourcespecification mapping of the provided list.
     *
     * @throws IllegalArgumentException if multiple resource specifications have matching names
     */
    public static Map<String, ResourceSpecification> getResourceSpecMap(
            Collection<ResourceSpecification> resourceSpecifications) throws IllegalArgumentException {
        Map<String, ResourceSpecification> resourceMap = new HashMap<>();
        for (ResourceSpecification resourceSpecification : resourceSpecifications) {
            ResourceSpecification prevValue = resourceMap.put(resourceSpecification.getName(), resourceSpecification);
            if (prevValue != null) {
                throw new IllegalArgumentException(String.format(
                        "Resources for a given task may not share the same name. " +
                                "name:'%s' oldResource:'%s' newResource:'%s'",
                        resourceSpecification.getName(), prevValue, resourceSpecification));
            }
        }

        return resourceMap;
    }

    /**
     * Returns a path=>template mapping of the provided {@link ConfigFileSpecification}s. Assumes
     * that each config file is given a distinct path.
     *
     * @throws IllegalArgumentException if multiple config specifications have matching relative path strings
     */
    private static Map<String, String> getConfigTemplateMap(
            Collection<ConfigFileSpecification> configSpecifications) throws IllegalArgumentException {
        Map<String, String> configMap = new HashMap<>();
        for (ConfigFileSpecification configSpecification : configSpecifications) {
            String prevValue =
                    configMap.put(configSpecification.getRelativePath(), configSpecification.getTemplateContent());
            if (prevValue != null) {
                throw new IllegalArgumentException(String.format(
                        "Config templates for a given task may not share the same path. " +
                                "path:'%s' oldContent:'%s' newContent:'%s'",
                        configSpecification.getRelativePath(), prevValue, configSpecification.getTemplateContent()));
            }
        }
        return configMap;
    }

    /**
     * Returns a {@link CommandInfo.URI} that wraps the given URI string.
     *
     * @param uri The URI to be encapsulated
     */
    public static CommandInfo.URI uri(String uri) {
        return CommandInfo.URI.newBuilder().setValue(uri).build();
    }

    public static TaskSpec.GoalState getGoalState(PodInstance podInstance, String taskName) throws TaskException {
        Optional<TaskSpec> taskSpec = getTaskSpec(podInstance, taskName);
        if (taskSpec.isPresent()) {
            return taskSpec.get().getGoal();
        } else {
            throw new TaskException("Failed to determine the goal state of Task: " + taskName);
        }
    }

    public static Optional<TaskSpec> getTaskSpec(PodInstance podInstance, String taskName) {
        return podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec).equals(taskName))
                .findFirst();
    }


    public static Map<PodInstance, List<TaskInfo>> getPodMap(
            ConfigStore<ServiceSpec> configStore,
            Collection<TaskInfo> taskInfos)
            throws TaskException {
        Map<PodInstance, List<TaskInfo>> podMap = new HashMap<>();

        for (TaskInfo taskInfo : taskInfos) {
            PodInstance podInstance = getPodInstance(configStore, taskInfo);
            List<TaskInfo> taskList = podMap.get(podInstance);

            if (taskList == null) {
                taskList = Arrays.asList(taskInfo);
            } else {
                taskList = new ArrayList<>(taskList);
                taskList.add(taskInfo);
            }

            podMap.put(podInstance, taskList);
        }

        return podMap;
    }

    public static PodInstance getPodInstance(
            ConfigStore<ServiceSpec> configStore,
            TaskInfo taskInfo) throws TaskException {

        PodSpec podSpec = getPodSpec(configStore, taskInfo);
        Integer index = CommonTaskUtils.getIndex(taskInfo);

        return new DefaultPodInstance(podSpec, index);
    }

    public static PodSpec getPodSpec(
            ConfigStore<ServiceSpec> configStore,
            TaskInfo taskInfo) throws TaskException {

        UUID configId = CommonTaskUtils.getTargetConfiguration(taskInfo);
        ServiceSpec serviceSpec;

        try {
            serviceSpec = configStore.fetch(configId);
        } catch (ConfigStoreException e) {
            throw new TaskException(String.format(
                    "Unable to retrieve ServiceSpecification ID %s referenced by TaskInfo[%s]",
                    configId, taskInfo.getName()), e);
        }

        PodSpec podSpec = TaskUtils.getPodSpec(serviceSpec, taskInfo);
        if (podSpec == null) {
            throw new TaskException(String.format(
                    "No TaskSpecification found for TaskInfo[%s]", taskInfo.getName()));
        }
        return podSpec;
    }
}
