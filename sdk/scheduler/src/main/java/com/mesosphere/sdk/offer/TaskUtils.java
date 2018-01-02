package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.PORTS_RESOURCE_TYPE;

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
    public static Optional<PodSpec> getPodSpec(ServiceSpec serviceSpec, TaskInfo taskInfo) throws TaskException {
        String podType = new TaskLabelReader(taskInfo).getType();

        for (PodSpec podSpec : serviceSpec.getPods()) {
            if (podSpec.getType().equals(podType)) {
                return Optional.of(podSpec);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns all the Task names for a PodInstance.
     *
     * @param podInstance A PodInstance
     * @return A List of all the task names.
     */
    public static List<String> getTaskNames(PodInstance podInstance) {
        return podInstance.getPod().getTasks().stream()
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());
    }

    /**
     * Returns all the task names for a pod, after filtering based on the passed in list of tasks to launch.
     *
     * @param podInstance   A PodInstance
     * @param tasksToLaunch The names of TaskSpecs which should be launched.
     * @return A list of the appropriate task names.
     */
    public static List<String> getTaskNames(PodInstance podInstance, Collection<String> tasksToLaunch) {
        LOGGER.debug("PodInstance tasks: {}", TaskUtils.getTaskNames(podInstance));
        return podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());
    }

    /**
     * Returns whether any tasks within the provided {@link ServiceSpec} have transport encryption specs defined.
     */
    public static boolean hasTasksWithTLS(ServiceSpec serviceSpec) {
        List<TaskSpec> tasks = new ArrayList<>();
        serviceSpec.getPods().forEach(pod -> tasks.addAll(pod.getTasks()));

        return tasks.stream().anyMatch(taskSpec -> !taskSpec.getTransportEncryption().isEmpty());
    }

    /**
     * Returns the TaskInfos associated with a PodInstance if its ever been launched.  The list will be empty if the
     * PodInstance has never been launched.
     *
     * @param podInstance A PodInstance
     * @param stateStore  A StateStore to search for the appropriate TaskInfos.
     * @return The list of TaskInfos associated with a PodInstance.
     */
    public static List<TaskInfo> getPodTasks(PodInstance podInstance, StateStore stateStore) {
        return stateStore.fetchTasks().stream()
                .filter(taskInfo -> {
                    try {
                        return isSamePodInstance(taskInfo, podInstance);
                    } catch (TaskException e) {
                        LOGGER.error("Failed to find pod tasks with exception: ", e);
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns whether the provided {@link TaskInfo} (representing a launched task) and {@link PodInstance} (from the
     * {@link ServiceSpec}) are both effectively for the same pod instance.
     */
    public static boolean isSamePodInstance(TaskInfo taskInfo, PodInstance podInstance) throws TaskException {
        return isSamePodInstance(taskInfo, podInstance.getPod().getType(), podInstance.getIndex());
    }

    /**
     * Returns whether the provided {@link TaskInfo} is in the provided pod type and index.
     */
    public static boolean isSamePodInstance(TaskInfo taskInfo, String type, int index) throws TaskException {
        TaskLabelReader labels = new TaskLabelReader(taskInfo);
        return labels.getType().equals(type)
                && labels.getIndex() == index;
    }

    /**
     * Determines whether two TaskSpecs are different.
     *
     * @param oldTaskSpec The previous definition of a Task.
     * @param newTaskSpec The new definition of a Task.
     * @return true if the Tasks are different, false otherwise.
     */
    public static boolean areDifferent(TaskSpec oldTaskSpec, TaskSpec newTaskSpec) {

        // Names

        String oldTaskName = oldTaskSpec.getName();
        String newTaskName = newTaskSpec.getName();
        if (!Objects.equals(oldTaskName, newTaskName)) {
            LOGGER.debug("Task names '{}' and '{}' are different.", oldTaskName, newTaskName);
            return true;
        }

        // Goal States

        GoalState oldGoalState = oldTaskSpec.getGoal();
        GoalState newGoalState = newTaskSpec.getGoal();
        if (!Objects.equals(oldGoalState, newGoalState)) {
            LOGGER.debug("Task goals '{}' and '{}' are different.", oldGoalState, newGoalState);
            return true;
        }

        // Resources (custom comparison)

        Map<String, ResourceSpec> oldResourceMap =
                getResourceSpecMap(oldTaskSpec.getResourceSet().getResources());
        Map<String, ResourceSpec> newResourceMap =
                getResourceSpecMap(newTaskSpec.getResourceSet().getResources());

        if (oldResourceMap.size() != newResourceMap.size()) {
            LOGGER.debug("Resource lengths are different for old resources: '{}' and new resources: '{}'",
                    oldResourceMap, newResourceMap);
            return true;
        }

        for (Map.Entry<String, ResourceSpec> newEntry : newResourceMap.entrySet()) {
            String resourceName = newEntry.getKey();
            LOGGER.debug("Checking resource difference for: {}", resourceName);
            ResourceSpec oldResourceSpec = oldResourceMap.get(resourceName);
            if (oldResourceSpec == null) {
                LOGGER.debug("Resource not found: {}", resourceName);
                return true;
            } else if (!EqualsBuilder.reflectionEquals(oldResourceSpec, newEntry.getValue())) {
                LOGGER.debug("Resources are different.");
                return true;
            }
        }

        // Volumes (custom comparison)

        if (!volumesEqual(oldTaskSpec, newTaskSpec)) {
            LOGGER.debug("Task volumes '{}' and '{}' are different.",
                    oldTaskSpec.getResourceSet().getVolumes(),
                    newTaskSpec.getResourceSet().getVolumes());
            return true;
        }

        // CommandSpecs

        Optional<CommandSpec> oldCommand = oldTaskSpec.getCommand();
        Optional<CommandSpec> newCommand = newTaskSpec.getCommand();
        if (!Objects.equals(oldCommand, newCommand)) {
            LOGGER.debug("Task commands '{}' and '{}' are different.", oldCommand, newCommand);
            return true;
        }

        // Health checks

        Optional<HealthCheckSpec> oldHealthCheck = oldTaskSpec.getHealthCheck();
        Optional<HealthCheckSpec> newHealthCheck = newTaskSpec.getHealthCheck();
        if (!Objects.equals(oldHealthCheck, newHealthCheck)) {
            LOGGER.debug("Task healthchecks '{}' and '{}' are different.", oldHealthCheck, newHealthCheck);
            return true;
        }

        // Readiness checks

        Optional<ReadinessCheckSpec> oldReadinessCheck = oldTaskSpec.getReadinessCheck();
        Optional<ReadinessCheckSpec> newReadinessCheck = newTaskSpec.getReadinessCheck();
        if (!Objects.equals(oldReadinessCheck, newReadinessCheck)) {
            LOGGER.debug("Task readinesschecks '{}' and '{}' are different.", oldReadinessCheck, newReadinessCheck);
            return true;
        }

        // Config files

        Map<String, ConfigFileSpec> oldConfigMap = getConfigTemplateMap(oldTaskSpec.getConfigFiles());
        Map<String, ConfigFileSpec> newConfigMap = getConfigTemplateMap(newTaskSpec.getConfigFiles());
        if (!Objects.equals(oldConfigMap, newConfigMap)) {
            LOGGER.debug("Config templates '{}' and '{}' are different.", oldConfigMap, newConfigMap);
            return true;
        }

        // DiscoverySpecs

        Optional<DiscoverySpec> oldDiscoverySpec = oldTaskSpec.getDiscovery();
        Optional<DiscoverySpec> newDiscoverySpec = newTaskSpec.getDiscovery();
        if (!Objects.equals(oldDiscoverySpec, newDiscoverySpec)) {
            LOGGER.debug("DiscoverySpecs '{}' and '{}' are different.", oldDiscoverySpec, newDiscoverySpec);
            return true;
        }

        int oldTaskKillGracePeriodSeconds = oldTaskSpec.getTaskKillGracePeriodSeconds();
        int newTaskKillGracePeriodSeconds = newTaskSpec.getTaskKillGracePeriodSeconds();
        if (oldTaskKillGracePeriodSeconds != newTaskKillGracePeriodSeconds) {
            LOGGER.debug("TaskKillGracePeriodSeconds '{}' and '{}' are different.",
                    oldTaskKillGracePeriodSeconds, newTaskKillGracePeriodSeconds);
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
    private static Map<String, ResourceSpec> getResourceSpecMap(
            Collection<ResourceSpec> resourceSpecs) throws IllegalArgumentException {
        Map<String, ResourceSpec> resourceMap = new HashMap<>();
        for (ResourceSpec resourceSpec : resourceSpecs) {
            ResourceSpec prevValue = resourceMap.put(resourceSpec.getName(), resourceSpec);
            if (prevValue != null && !prevValue.getName().equals(PORTS_RESOURCE_TYPE)) {
                throw new IllegalArgumentException(String.format(
                        "Non-port resources for a given task may not share the same name. " +
                                "name:'%s' oldResource:'%s' newResource:'%s'",
                        resourceSpec.getName(), prevValue, resourceSpec));
            }
        }

        return resourceMap;
    }

    /**
     * Returns a name=>template mapping of the provided {@link ConfigFileSpec}s. Checks for unique paths and names
     * across all configs.
     *
     * @throws IllegalArgumentException if multiple config specifications have matching relative path strings
     */
    private static Map<String, ConfigFileSpec> getConfigTemplateMap(Collection<ConfigFileSpec> configSpecs)
            throws IllegalArgumentException {
        Set<String> configPaths = new HashSet<>();
        Map<String, ConfigFileSpec> configMap = new HashMap<>();
        for (ConfigFileSpec configSpec : configSpecs) {
            if (!configPaths.add(configSpec.getRelativePath())) {
                throw new IllegalArgumentException(String.format(
                        "Config templates for a given task may not share the same path: '%s'",
                        configSpec.getRelativePath()));

            }
            ConfigFileSpec prevSpec = configMap.put(configSpec.getName(), configSpec);
            if (prevSpec != null) {
                // A config of this name is already present in the map.
                throw new IllegalArgumentException(String.format(
                        "Config templates for a given task may not share the same name: '%s'",
                        configSpec.getName()));
            }
        }
        return configMap;
    }

    /**
     * Gets the {@link GoalState} of Task.
     *
     * @param podInstance A PodInstance containing tasks.
     * @param taskName    The name of the Task whose goal state is desired
     * @return The {@link GoalState} of the task.
     * @throws TaskException is thrown when unable to determine a task's {@link GoalState}
     */
    public static GoalState getGoalState(PodInstance podInstance, String taskName) throws TaskException {
        Optional<TaskSpec> taskSpec = getTaskSpec(podInstance, taskName);
        if (taskSpec.isPresent()) {
            return taskSpec.get().getGoal();
        } else {
            throw new TaskException("Failed to determine the goal state of Task: " + taskName);
        }
    }

    public static Optional<TaskSpec> getTaskSpec(ConfigStore<ServiceSpec> configStore, Protos.TaskInfo taskInfo)
            throws TaskException {
        return getTaskSpec(getPodInstance(configStore, taskInfo), taskInfo.getName());
    }

    public static Optional<TaskSpec> getTaskSpec(PodInstance podInstance, String taskName) {
        return podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec).equals(taskName))
                .findFirst();
    }

    public static Optional<TaskSpec> getTaskSpec(ServiceSpec serviceSpec, String podType, String taskName) {
        for (PodSpec podSpec : serviceSpec.getPods()) {
            if (!podSpec.getType().equals(podType)) {
                continue;
            }
            for (TaskSpec taskSpec : podSpec.getTasks()) {
                if (taskSpec.getName().equals(taskName)) {
                    return Optional.of(taskSpec);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Given a list of all tasks and failed tasks, returns a list of tasks (via returned
     * {@link PodInstanceRequirement#getTasksToLaunch()}) that should be relaunched.
     *
     * @param failedTasks tasks marked as needing recovery
     * @param allLaunchedTasks all launched tasks in the service
     * @return list of pods, each with contained named tasks to be relaunched
     */
    public static List<PodInstanceRequirement> getPodRequirements(
            ConfigStore<ServiceSpec> configStore,
            Collection<TaskInfo> failedTasks,
            Collection<TaskInfo> allLaunchedTasks) {

        // Mapping of pods, to failed tasks within those pods.
        // Arbitrary consistent ordering: by pod instance name (e.g. "otherpodtype-0","podtype-0","podtype-1")
        Map<PodInstance, Collection<TaskSpec>> podsToFailedTasks =
                new TreeMap<>(Comparator.comparing(PodInstance::getName));
        for (TaskInfo taskInfo : failedTasks) {
            try {
                PodInstance podInstance = getPodInstance(configStore, taskInfo);
                Optional<TaskSpec> taskSpec = getTaskSpec(podInstance, taskInfo.getName());
                if (!taskSpec.isPresent()) {
                    LOGGER.error("No TaskSpec found for failed task: {}", taskInfo.getName());
                    continue;
                }
                Collection<TaskSpec> failedTaskSpecs = podsToFailedTasks.get(podInstance);
                if (failedTaskSpecs == null) {
                    failedTaskSpecs = new ArrayList<>();
                    podsToFailedTasks.put(podInstance, failedTaskSpecs);
                }
                failedTaskSpecs.add(taskSpec.get());
            } catch (TaskException e) {
                LOGGER.error(String.format("Failed to get pod instance for task: %s", taskInfo.getName()), e);
            }
        }
        if (podsToFailedTasks.isEmpty()) {
            // short circuit
            return Collections.emptyList();
        }

        // Log failed pod map
        for (Map.Entry<PodInstance, Collection<TaskSpec>> entry : podsToFailedTasks.entrySet()) {
            List<String> taskNames = entry.getValue().stream()
                    .map(taskSpec -> taskSpec.getName())
                    .collect(Collectors.toList());
            LOGGER.info("Failed pod: {} with tasks: {}", entry.getKey().getName(), taskNames);
        }

        Set<String> allLaunchedTaskNames = allLaunchedTasks.stream()
                .map(taskInfo -> taskInfo.getName())
                .collect(Collectors.toSet());

        List<PodInstanceRequirement> podInstanceRequirements = new ArrayList<>();
        for (Map.Entry<PodInstance, Collection<TaskSpec>> entry : podsToFailedTasks.entrySet()) {
            boolean anyFailedTasksAreEssential = entry.getValue().stream().anyMatch(taskSpec -> taskSpec.isEssential());
            Collection<TaskSpec> taskSpecsToLaunch;
            if (anyFailedTasksAreEssential) {
                // One or more of the failed tasks in this pod are marked as 'essential'.
                // Relaunch all applicable tasks in the pod.
                taskSpecsToLaunch = entry.getKey().getPod().getTasks();
            } else {
                // None of the failed tasks in this pod are 'essential'.
                // Only recover the failed task(s), leave others in the pod as-is.
                taskSpecsToLaunch = entry.getValue();
            }

            // Additional filtering:
            // - Only relaunch tasks that have a RUNNING goal state. Don't worry about FINISHED tasks.
            // - Don't relaunch tasks that haven't been launched yet (as indicated by presence in allLaunchedTasks)
            taskSpecsToLaunch = taskSpecsToLaunch.stream()
                    .filter(taskSpec -> taskSpec.getGoal() == GoalState.RUNNING &&
                            allLaunchedTaskNames.contains(TaskSpec.getInstanceName(entry.getKey(), taskSpec.getName())))
                    .collect(Collectors.toList());

            if (taskSpecsToLaunch.isEmpty()) {
                LOGGER.info("No tasks to recover for pod: {}", entry.getKey().getName());
                continue;
            }

            LOGGER.info("Tasks to relaunch in pod {}: {}", entry.getKey().getName(), taskSpecsToLaunch.stream()
                    .map(taskSpec -> String.format(
                            "%s=%s", taskSpec.getName(), taskSpec.isEssential() ? "essential" : "nonessential"))
                    .collect(Collectors.toList()));
            podInstanceRequirements.add(PodInstanceRequirement.newBuilder(
                    entry.getKey(),
                    taskSpecsToLaunch.stream()
                            .map(taskSpec -> taskSpec.getName())
                            .collect(Collectors.toList()))
                    .build());
        }

        return podInstanceRequirements;
    }

    public static PodInstance getPodInstance(ConfigStore<ServiceSpec> configStore, TaskInfo taskInfo)
            throws TaskException {
        return getPodInstance(getPodSpec(configStore, taskInfo), taskInfo);
    }

    public static PodInstance getPodInstance(PodSpec podSpec, TaskInfo taskInfo) throws TaskException {
        return new DefaultPodInstance(podSpec, new TaskLabelReader(taskInfo).getIndex());
    }

    private static PodSpec getPodSpec(ConfigStore<ServiceSpec> configStore, TaskInfo taskInfo) throws TaskException {
        UUID configId = new TaskLabelReader(taskInfo).getTargetConfiguration();
        ServiceSpec serviceSpec;

        try {
            serviceSpec = configStore.fetch(configId);
        } catch (ConfigStoreException e) {
            throw new TaskException(String.format(
                    "Unable to retrieve ServiceSpecification ID %s referenced by TaskInfo[%s]",
                    configId, taskInfo.getName()), e);
        }

        Optional<PodSpec> podSpecOptional = getPodSpec(serviceSpec, taskInfo);
        if (!podSpecOptional.isPresent()) {
            throw new TaskException(String.format(
                    "No TaskSpecification found for TaskInfo[%s]", taskInfo.getName()));
        } else {
            return podSpecOptional.get();
        }
    }

    /**
     * Returns whether the provided {@link TaskStatus} shows that the task needs to recover.
     */
    public static boolean isRecoveryNeeded(TaskStatus taskStatus) {
        switch (taskStatus.getState()) {
            case TASK_FINISHED:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_ERROR:
            case TASK_LOST:
                return true;
            case TASK_KILLING:
            case TASK_RUNNING:
            case TASK_STAGING:
            case TASK_STARTING:
                break;
        }

        return false;
    }

    /**
     * Returns whether the provided {@link TaskStatus} has reached a terminal state.
     */
    public static boolean isTerminal(TaskStatus taskStatus) {
        return isTerminal(taskStatus.getState());
    }

    /**
     * Returns whether the provided {@link TaskState} has reached a terminal state.
     */
    public static boolean isTerminal(TaskState taskState) {
        switch (taskState) {
            case TASK_FINISHED:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_ERROR:
                return true;
            case TASK_LOST:
            case TASK_KILLING:
            case TASK_RUNNING:
            case TASK_STAGING:
            case TASK_STARTING:
                break;
        }

        return false;
    }

    /**
     * Determines whether a Task needs to be recovered based on its current definition (TaskSpec) and status
     * (TaskStatus).
     *
     * @param taskSpec   The definition of a task
     * @param taskStatus The status of the task.
     * @return true if recovery is needed, false otherwise.
     */
    public static boolean needsRecovery(TaskSpec taskSpec, TaskStatus taskStatus) {
        // Tasks with a goal state of finished should never leave the purview of their original
        // plan, so they are not the responsibility of recovery.  Recovery only applies to Tasks
        // which reached their goal state of RUNNING and then later failed.
        if (taskSpec.getGoal().equals(GoalState.ONCE) || taskSpec.getGoal().equals(GoalState.FINISH)) {
            return false;
        } else {
            return isRecoveryNeeded(taskStatus);
        }
    }

    /**
     * Returns a default name for a {@link Step} given a PodInstance and the tasks to be launched in it.
     *
     * @param podInstance   The PodInstance to be launched by a {@link Step}.
     * @param tasksToLaunch The tasks to be launched in the Pod.
     * @return The {@link Step} name
     */
    public static String getStepName(PodInstance podInstance, Collection<String> tasksToLaunch) {
        return podInstance.getName() + ":" + tasksToLaunch;
    }

    /**
     * Returns TaskInfos will all reservations and persistence IDs removed from their Resources.
     */
    public static Collection<TaskInfo> clearReservations(Collection<TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(TaskUtils::clearReservationIds)
                .collect(Collectors.toList());
    }

    private static TaskInfo clearReservationIds(TaskInfo taskInfo) {
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .addAllResources(clearReservationIds(taskInfo.getResourcesList()));

        if (taskInfo.hasExecutor()) {
            taskInfoBuilder.getExecutorBuilder()
                    .clearResources()
                    .addAllResources(clearReservationIds(taskInfoBuilder.getExecutor().getResourcesList()));
        }

        return taskInfoBuilder.build();
    }

    private static List<Resource> clearReservationIds(List<Resource> resources) {
        List<Resource> clearedResources = new ArrayList<>();
        for (Resource resource : resources) {
            clearedResources.add(ResourceBuilder.fromExistingResource(resource)
                    .clearResourceId()
                    .clearPersistenceId()
                    .build());
        }
        return clearedResources;
    }

    /**
     * Determines if a task is launched in any zones.
     * @param taskInfo The {@link TaskInfo} to get zone information from.
     * @return A boolean indicating whether the task is in a zone.
     */
    public static boolean taskHasZone(TaskInfo taskInfo) {
        return taskInfo.getCommand().getEnvironment().getVariablesList().stream()
                .anyMatch(variable -> variable.getName().equals(EnvConstants.ZONE_TASKENV));
    }

    /**
     * Gets the zone of a task.
     * @param taskInfo The {@link TaskInfo} to get zone information from.
     * @return A string indicating the zone the task is in.
     */
    public static String getTaskZone(TaskInfo taskInfo) {
        return taskInfo.getCommand().getEnvironment().getVariablesList().stream()
            .filter(variable -> variable.getName().equals(EnvConstants.ZONE_TASKENV)).findFirst().get().getValue();
    }

    /**
     * Gets the IP address associated with the task.
     * @param taskStatus the {@link TaskStatus} to get the IP address from.
     * @return A String indicating the IP address associated with the task.
     */
    public static String getTaskIPAddress(TaskStatus taskStatus) throws IllegalStateException {
        List<Protos.NetworkInfo> networkInfo = taskStatus.getContainerStatus().getNetworkInfosList();
        if (networkInfo.isEmpty()) {
            throw new IllegalStateException(
                    String.format("No network info can be found for the task info: %s", taskStatus.toString()));
        }
        return networkInfo.stream().findFirst().get().getIpAddresses(0).getIpAddress();
    }
}
