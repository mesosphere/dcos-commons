package com.mesosphere.sdk.offer;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */
public class TaskUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskUtils.class);
    private static final int CONFIG_TEMPLATE_LIMIT_BYTES = 512 * 1024; // 512KB
    public static final String CONFIG_TEMPLATE_KEY_PREFIX = "config_template:";
    public static final String TARGET_CONFIGURATION_KEY = "target_configuration";
    public static final String TASK_NAME_DELIM = "__";
    public static final String COMMAND_DATA_PACKAGE_EXECUTOR = "command_data_package_executor";
    public static final String GOAL_STATE_KEY = "goal_state";
    public static final String TASK_NAME_KEY = "TASK_NAME";
    public static final String TRANSIENT_FLAG_KEY = "transient";
    public static final String TASK_SPEC_KEY = "transient";

    private TaskUtils() {
        // do not instantiate
    }

    /**
     * Returns the {@link TaskSpec} in the provided {@link com.mesosphere.sdk.specification.DefaultServiceSpec}
     * which matches the provided {@link TaskInfo}, or {@code null} if no match could be found.
     */
    public static PodSpec getPodSpec(ServiceSpec serviceSpec, TaskInfo taskInfo) throws TaskException {
        String podType = TaskUtils.getType(taskInfo);

        for (PodSpec podSpec : serviceSpec.getPods()) {
            if (podSpec.getType().equals(podType)) {
                return podSpec;
            }
        }

        return null;
    }

    /**
     * Returns all the Task names for a PodInstance.
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
     * @param podInstance A PodInstance
     * @param tasksToLaunch The names of TaskSpecs which should be launched.
     * @return A list of the appropriate task names.
     */
    public static List<String> getTaskNames(PodInstance podInstance, Collection<String> tasksToLaunch) {
        LOGGER.info("PodInstance tasks: {}", TaskUtils.getTaskNames(podInstance));
        return podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());
    }

    /**
     * Returns the TaskInfos associated with a PodInstance if its ever been launched.  The list will be empty if the
     * PodInstance has never been launched.
     * @param podInstance A PodInstance
     * @param stateStore A StateStore to search for the appropriate TaskInfos.
     * @return The list of TaskInfos associated with a PodInstance.
     */
    public static List<TaskInfo> getPodTasks(PodInstance podInstance, StateStore stateStore) {
        return stateStore.fetchTasks().stream()
                .filter(taskInfo -> {
                    try {
                        return TaskUtils.getType(taskInfo).equals(podInstance.getName());
                    } catch (TaskException e) {
                        LOGGER.error("Encountered ");
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the TaskInfos for a PodInstance which should be running.  The list will be empty if the PodInstance has
     * never been launched.
     * @param podInstance A PodInstance
     * @param stateStore A StateStore to search for the appropriate TaskInfos.
     * @return The list of TaskInfos associate with a PodInstance which should be running.
     */
    public static List<TaskInfo> getTaskInfosShouldBeRunning(PodInstance podInstance, StateStore stateStore) {
        List<TaskInfo> podTasks = getPodTasks(podInstance, stateStore);

        List<TaskInfo> tasksShouldBeRunning = new ArrayList<>();
        for (TaskInfo taskInfo : podTasks) {
            Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(taskInfo, podInstance);

            if (taskSpecOptional.isPresent() && taskSpecOptional.get().getGoal().equals(GoalState.RUNNING)) {
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

    /**
     * Returns the ExecutorInfo of a PodInstance if it is still running so it may be re-used.
     * @param podInstance A PodInstance
     * @param stateStore A StateStore to search for the appropriate TaskInfos.
     * @return The ExecutorInfo if the Executor is running, Optional.empty() otherwise.
     */
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


    /**
     * Determines whether two TaskSpecs are different.
     * @param oldTaskSpec The previous definition of a Task.
     * @param newTaskSpec The new definition of a Task.
     * @return true if the Tasks are different, false otherwise.
     */
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
            LOGGER.debug("Checking resource difference for: {}", resourceName);
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
    private static Map<String, ResourceSpecification> getResourceSpecMap(
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

    /**
     * Sets a label on a TaskInfo indicating the Task's {@link GoalState}.
     * @param taskInfoBuilder The TaskInfo to be labeled.
     * @param taskSpec The TaskSpec containing the goal state.
     * @return The labeled TaskInfo
     */
    public static TaskInfo.Builder setGoalState(TaskInfo.Builder taskInfoBuilder, TaskSpec taskSpec) {
        return taskInfoBuilder
                .setLabels(TaskUtils.withLabelSet(taskInfoBuilder.getLabels(),
                        TaskUtils.GOAL_STATE_KEY,
                        taskSpec.getGoal().name()));
    }

    /**
     * Gets the {@link GoalState} of Task.
     * @param podInstance A PodInstance containing tasks.
     * @param taskName The name of the Task whose goal state is desired
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
        Integer index = TaskUtils.getIndex(taskInfo);

        return new DefaultPodInstance(podSpec, index);
    }

    public static PodSpec getPodSpec(
            ConfigStore<ServiceSpec> configStore,
            TaskInfo taskInfo) throws TaskException {

        UUID configId = TaskUtils.getTargetConfiguration(taskInfo);
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

    /**
     * Determines whether a Task needs to eb reovered based on its current definition (TaskSpec) and status
     * (TaskStatus).
     * @param taskSpec The definition of a task
     * @param taskStatus The status of the task.
     * @return true if recovery is needed, false otherwise.
     */
    public static boolean needsRecovery(TaskSpec taskSpec, TaskStatus taskStatus) {
        if (taskSpec.getGoal() == GoalState.FINISHED && taskStatus.getState() == TaskState.TASK_FINISHED) {
            return false;
        } else {
            return TaskUtils.needsRecovery(taskStatus);
        }
    }

    /**
     * Returns a default name for a {@link Step} given a PodInstance and the tasks to be launched in it.
     * @param podInstance The PodInstance to be launched by a {@link Step}.
     * @param tasksToLaunch The tasks to be launched in the Pod.
     * @return The {@link Step} name
     */
    public static String getStepName(PodInstance podInstance, Collection<String> tasksToLaunch) {
        return podInstance.getName() + ":" + tasksToLaunch;
    }

    public static void setTaskSpec(
            TaskInfo.Builder taskInfoBuilder,
            TaskSpec taskSpec) {
        try {
            taskInfoBuilder
                    .setLabels(TaskUtils.withLabelSet(taskInfoBuilder.getLabels(),
                            TaskUtils.TASK_SPEC_KEY,
                            SerializationUtils.toJsonString(taskSpec)));
        } catch (IOException e) {
            LOGGER.error("Failed to serialize TaskSpec.", e);
        }
    }

    public static void getTaskSpec(TaskInfo taskInfo) {
    }

    /**
     * Label key against which Offer attributes are stored (in a string representation).
     */
    private static final String OFFER_ATTRIBUTES_KEY = "offer_attributes";

    /**
     * Label key against which the offer agent's hostname is stored.
     */
    private static final String OFFER_HOSTNAME_KEY = "offer_hostname";

    /**
     * Label key against which the Task Type is stored.
     */
    protected static final String TYPE_KEY = "task_type";
    protected static final String INDEX_KEY = "index";

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "instance-0__aoeu5678" => "instance-0"
     */
    public static String toTaskName(TaskID taskId) throws TaskException {
        int underScoreIndex = taskId.getValue().lastIndexOf(TASK_NAME_DELIM);

        if (underScoreIndex == -1) {
            throw new TaskException(String.format(
                    "TaskID '%s' is malformed.  Expected '%s' to extract TaskName from TaskID.  "
                            + "TaskIDs should be generated with CommonTaskUtils.toTaskId().", taskId, TASK_NAME_DELIM));
        }

        return taskId.getValue().substring(0, underScoreIndex);
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "instance-0" => "instance-0__aoeu5678"
     */
    public static TaskID toTaskId(String taskName) {
        return TaskID.newBuilder()
                .setValue(taskName + TASK_NAME_DELIM + UUID.randomUUID())
                .build();
    }

    public static TaskID emptyTaskId() {
        return TaskID.newBuilder().setValue("").build();
    }

    public static SlaveID emptyAgentId() {
        return SlaveID.newBuilder().setValue("").build();
    }

    public static ExecutorID emptyExecutorId() {
        return ExecutorID.newBuilder().setValue("").build();
    }

    /**
     * Returns whether the provided {@link TaskStatus} shows that the task needs to recover.
     */
    public static boolean needsRecovery(TaskStatus taskStatus) {
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
     * Returns whether the provided {@link TaskStatus} shows that the task has reached a terminal state.
     */
    public static boolean isTerminal(TaskStatus taskStatus) {
        switch (taskStatus.getState()) {
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
     * Ensures that the provided {@link TaskInfo} contains a {@link Label} identifying it as a
     * transient task.
     */
    public static TaskInfo setTransient(TaskInfo taskInfo) {
        return taskInfo.toBuilder()
                .setLabels(withLabelSet(taskInfo.getLabels(),
                        TaskUtils.TRANSIENT_FLAG_KEY,
                        "true"))
                .build();
    }

    /**
     * Ensures that the provided {@link TaskInfo} does not contain a {@link Label} identifying it as
     * a transient task.
     */
    public static TaskInfo clearTransient(TaskInfo taskInfo) {
        return taskInfo.toBuilder()
                .setLabels(withLabelRemoved(taskInfo.getLabels(), TaskUtils.TRANSIENT_FLAG_KEY))
                .build();
    }

    /**
     * Stores the provided Task Type string into the {@link TaskInfo} as a {@link Label}. Any
     * existing stored task type is overwritten.
     */
    public static TaskInfo.Builder setType(TaskInfo.Builder taskBuilder, String taskType) {
        return taskBuilder.setLabels(withLabelSet(taskBuilder.getLabels(), TYPE_KEY, taskType));
    }

    /**
     * Returns the task type string, which was embedded in the provided {@link TaskInfo}.
     *
     * @throws TaskException if the type could not be found.
     */
    public static String getType(TaskInfo taskInfo) throws TaskException {
        Optional<String> taskType = findLabelValue(taskInfo.getLabels(), TYPE_KEY);
        if (!taskType.isPresent()) {
            LOGGER.error("TaskInfo: {} does not contain a label indicating type.", taskInfo);
            throw new TaskException("TaskInfo does not contain label with key: " + TYPE_KEY);
        }
        return taskType.get();
    }

    /**
     * Assigns the pod instance index to the provided task.
     */
    public static TaskInfo.Builder setIndex(TaskInfo.Builder taskBuilder, int index) {
        return taskBuilder.setLabels(withLabelSet(taskBuilder.getLabels(), INDEX_KEY, String.valueOf(index)));
    }

    /**
     * Returns the pod instance index of the provided task, or throws {@link TaskException} if no index data was found.
     *
     * @throws TaskException if the index data wasn't found
     * @throws NumberFormatException if parsing the index as an integer failed
     */
    public static int getIndex(TaskInfo taskInfo) throws TaskException {
        Optional<String> index = findLabelValue(taskInfo.getLabels(), INDEX_KEY);
        if (!index.isPresent()) {
            throw new TaskException("TaskInfo does not contain label with key: " + INDEX_KEY);
        }
        return Integer.parseInt(index.get());
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer} into the {@link TaskInfo} as a
     * {@link Label}. Any existing stored attributes are overwritten.
     */
    public static TaskInfo.Builder setOfferAttributes(TaskInfo.Builder taskBuilder, Offer launchOffer) {
        return taskBuilder
                .setLabels(withLabelSet(taskBuilder.getLabels(),
                        OFFER_ATTRIBUTES_KEY,
                        AttributeStringUtils.toString(launchOffer.getAttributesList())));
    }

    /**
     * Returns the string representations of any {@link Offer} {@link Attribute}s which were
     * embedded in the provided {@link TaskInfo}.
     */
    public static List<String> getOfferAttributeStrings(TaskInfo taskInfo) {
        Optional<String> joinedAttributes = findLabelValue(taskInfo.getLabels(), OFFER_ATTRIBUTES_KEY);
        if (!joinedAttributes.isPresent()) {
            return new ArrayList<>();
        }
        return AttributeStringUtils.toStringList(joinedAttributes.get());
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer} into the {@link TaskInfo} as a
     * {@link Label}. Any existing stored attributes are overwritten.
     */
    public static TaskInfo.Builder setHostname(TaskInfo.Builder taskBuilder, Offer launchOffer) {
        return taskBuilder.setLabels(
                withLabelSet(taskBuilder.getLabels(), OFFER_HOSTNAME_KEY, launchOffer.getHostname()));
    }

    /**
     * Returns the string representations of any {@link Offer} {@link Attribute}s which were
     * embedded in the provided {@link TaskInfo}.
     */
    public static String getHostname(TaskInfo taskInfo) throws TaskException {
        Optional<String> hostname = findLabelValue(taskInfo.getLabels(), OFFER_HOSTNAME_KEY);
        if (!hostname.isPresent()) {
            throw new TaskException("TaskInfo does not contain label with key: " + OFFER_HOSTNAME_KEY);
        }
        return hostname.get();
    }

    /**
     * Sets a {@link Label} indicating the target configuration for the provided {@link TaskInfo}.
     *
     * @param taskInfoBuilder       is the TaskInfo which will have the appropriate configuration {@link Label} set.
     * @param targetConfigurationId is the ID referencing a particular Configuration in the {@link ConfigStore}
     */
    public static TaskInfo.Builder setTargetConfiguration(
            TaskInfo.Builder taskInfoBuilder, UUID targetConfigurationId) {
        return taskInfoBuilder
                .setLabels(withLabelSet(taskInfoBuilder.getLabels(),
                        TARGET_CONFIGURATION_KEY,
                        targetConfigurationId.toString()));
    }

    /**
     * Returns the ID referencing a configuration in a {@link ConfigStore} associated with the provided
     * {@link TaskInfo}.
     *
     * @param taskInfo is the TaskInfo from which the the configuration ID will be extracted.
     * @return the ID of the target configuration for the provided {@link TaskInfo}
     * @throws TaskException when a TaskInfo is provided which does not contain a {@link Label} with
     *                       an indicated target configuration
     */
    public static UUID getTargetConfiguration(TaskInfo taskInfo) throws TaskException {
        Optional<String> value = findLabelValue(taskInfo.getLabels(), TARGET_CONFIGURATION_KEY);
        if (!value.isPresent()) {
            throw new TaskException("TaskInfo does not contain label with key: " + TARGET_CONFIGURATION_KEY);
        }
        return UUID.fromString(value.get());
    }


    /**
     * Retrieves the config file data, if any, from the provided {@link TaskInfo}'s {@code labels}
     * field. If no data is found, returns an empty collection.
     */
    public static Collection<ConfigFileSpecification> getConfigFiles(TaskInfo taskInfo)
            throws InvalidProtocolBufferException {
        List<ConfigFileSpecification> configs = new ArrayList<>();
        for (Label label : taskInfo.getLabels().getLabelsList()) {
            // Extract all labels whose key has the expected prefix:
            if (!label.getKey().startsWith(CONFIG_TEMPLATE_KEY_PREFIX)) {
                continue;
            }
            configs.add(new DefaultConfigFileSpecification(
                    label.getKey().substring(CONFIG_TEMPLATE_KEY_PREFIX.length()),
                    label.getValue()));
        }
        return configs;
    }

    /**
     * Extracts the environment variables given in the {@link Environment}.
     *
     * @param environment The {@link Environment} to extract environment variables from
     * @return The map containing environment variables
     */
    public static Map<String, String> fromEnvironmentToMap(Environment environment) {
        Map<String, String> map = new HashMap<>();

        final List<Environment.Variable> variables = environment.getVariablesList();

        for (Environment.Variable variable : variables) {
            map.put(variable.getName(), variable.getValue());
        }

        return map;
    }

    /**
     * Extracts and puts environment variables from the given map to a {@link Environment}.
     *
     * @param environmentMap The map to extract environment variables from
     * @return The {@link Environment} containing the extracted environment variables
     */
    public static Environment fromMapToEnvironment(Map<String, String> environmentMap) {
        if (environmentMap == null) {
            return Environment.getDefaultInstance();
        }

        Collection<Environment.Variable> vars = environmentMap
                .entrySet()
                .stream()
                .map(entrySet -> Environment.Variable.newBuilder()
                        .setName(entrySet.getKey())
                        .setValue(entrySet.getValue()).build())
                .collect(Collectors.toList());

        return Environment.newBuilder().addAllVariables(vars).build();
    }

    /**
     * Invokes {@link #sendStatus(ExecutorDriver, TaskState, TaskID, SlaveID, ExecutorID, String, byte[])} with a null
     * {@code data} value.
     */
    public static void sendStatus(ExecutorDriver driver,
                                  TaskState state,
                                  TaskID taskID,
                                  SlaveID slaveID,
                                  ExecutorID executorID,
                                  String message) {
        sendStatus(driver, state, taskID, slaveID, executorID, message, null);
    }

    /**
     * Sends a {@link TaskStatus} to the provided {@code driver} which contains the provided information.
     */
    public static void sendStatus(ExecutorDriver driver,
                                  TaskState state,
                                  TaskID taskID,
                                  SlaveID slaveID,
                                  ExecutorID executorID,
                                  String message,
                                  byte[] data) {
        final TaskStatus.Builder builder = TaskStatus.newBuilder();

        builder.setState(state);
        builder.setMessage(message);
        builder.setTaskId(taskID);
        builder.setSlaveId(slaveID);
        builder.setExecutorId(executorID);
        builder.setSource(TaskStatus.Source.SOURCE_EXECUTOR);

        if (data != null) {
            builder.setData(ByteString.copyFrom(data));
        }

        final TaskStatus taskStatus = builder.build();
        driver.sendStatusUpdate(taskStatus);
    }

    /**
     * Mesos requirements do not allow a TaskInfo to simultaneously have a Command and Executor.  In order to
     * workaround this we encapsulate a TaskInfo's Command and Data fields in an ExecutorInfo and store it in the
     * data field of the TaskInfo.
     */
    public static TaskInfo packTaskInfo(TaskInfo taskInfo) {
        if (!taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            ExecutorInfo.Builder executorInfoBuilder = ExecutorInfo.newBuilder()
                    .setExecutorId(ExecutorID.newBuilder().setValue(COMMAND_DATA_PACKAGE_EXECUTOR));

            if (taskInfo.hasCommand()) {
                executorInfoBuilder.setCommand(taskInfo.getCommand());
            } else {
                executorInfoBuilder.setCommand(CommandInfo.getDefaultInstance());
            }

            if (taskInfo.hasData()) {
                executorInfoBuilder.setData(taskInfo.getData());
            }

            return TaskInfo.newBuilder(taskInfo)
                    .setData(executorInfoBuilder.build().toByteString())
                    .clearCommand()
                    .build();
        }
    }

    /**
     * This method reverses the work done in {@link #packTaskInfo(TaskInfo)} such that the original
     * TaskInfo is regenerated.
     */
    public static TaskInfo unpackTaskInfo(TaskInfo taskInfo) throws InvalidProtocolBufferException {
        if (!taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
            ExecutorInfo pkgExecutorInfo = ExecutorInfo.parseFrom(taskInfo.getData());

            if (pkgExecutorInfo.hasCommand()) {
                taskBuilder.setCommand(pkgExecutorInfo.getCommand());
            }

            if (pkgExecutorInfo.hasData()) {
                taskBuilder.setData(pkgExecutorInfo.getData());
            }

            return taskBuilder.build();
        }
    }

    public static ProcessBuilder getProcess(TaskInfo taskInfo) throws InvalidProtocolBufferException {
        CommandInfo commandInfo = taskInfo.getCommand();
        String cmd = commandInfo.getValue();

        ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", cmd);
        builder.inheritIO();
        builder.environment().putAll(TaskUtils.fromEnvironmentToMap(commandInfo.getEnvironment()));

        return builder;
    }


    /**
     * Returns the value of a {@link Label} named {@code key}, or returns {@code null} if no
     * matching {@link Label} is found.
     */
    public static Optional<String> findLabelValue(Labels labels, String key) {
        for (Label label : labels.getLabelsList()) {
            if (label.getKey().equals(key)) {
                return Optional.of(label.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Removes any preexisting label with the provided {@code labelKey}, or makes no changes if no
     * matching {@link Label} was found.
     *
     * @return an updated {@link Labels.Builder} with the requested label removed
     */
    private static Labels.Builder withLabelRemoved(Labels labels, String labelKey) {
        Labels.Builder labelsBuilder = Labels.newBuilder();

        for (Label label : labels.getLabelsList()) {
            if (!label.getKey().equals(labelKey)) {
                labelsBuilder.addLabels(label);
            }
        }

        return labelsBuilder;
    }

    /**
     * Removes any preexisting label with the provided {@code labelKey} and adds a new {@link Label}
     * with the provided {@code labelKey}/{@code labelValue}.
     *
     * @return an updated {@link Labels.Builder} with the requested label
     */
    public static Labels.Builder withLabelSet(Labels labels, String labelKey, String labelValue) {
        Labels.Builder labelsBuilder = withLabelRemoved(labels, labelKey);
        labelsBuilder.addLabelsBuilder()
                .setKey(labelKey)
                .setValue(labelValue);
        return labelsBuilder;
    }

    /**
     * Injects the proper data into the given config template and writes the populated template to disk.
     *
     * @param relativePath    The path to write the file
     * @param templateContent The content of the config template
     * @param environment     The environment from which to extract the injection data
     * @throws IOException if the data can't be written to disk
     */
    protected static void writeConfigFile(
            String relativePath,
            String templateContent,
            Map<String, String> environment) throws IOException {

        LOGGER.info("Writing config file: {}", relativePath);

        File configFile = new File(relativePath);
        Writer writer = null;

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                throw new IOException(String.format("Can't create config file %s: %s", relativePath, e));
            }
        }

        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(relativePath), "utf-8"));
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(new StringReader(templateContent), "configTemplate");
            mustache.execute(writer, environment);
            writer.close();
        } catch (IOException e) {
            if (writer != null) {
                writer.close();
            }
            throw new IOException(String.format("Can't write to file %s: %s", relativePath, e));
        }
    }

    /**
     * Renders a given Mustache template using the provided environment map.
     *
     * @param templateContent String representation of template.
     * @param environment     Map of environment variables.
     * @return Rendered Mustache template String.
     */
    public static String applyEnvToMustache(String templateContent, Map<String, String> environment) {
        StringWriter writer = new StringWriter();
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new StringReader(templateContent), "configTemplate");
        mustache.execute(writer, environment);
        return writer.toString();
    }

    public static boolean isMustacheFullyRendered(String templateContent) {
        return StringUtils.isEmpty(templateContent) || !templateContent.matches("\\{\\{.*\\}\\}");
    }

    /**
     * Sets up the config files on the executor side.
     *
     * @param taskInfo The {@link TaskInfo} to extract the config file data from
     * @throws IOException if the data in the taskInfo is not valid or the config can't be written to disk
     */
    public static void setupConfigFiles(TaskInfo taskInfo) throws IOException {

        LOGGER.info("Setting up config files");
        final Map<String, String> environment =
                TaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        // some config templates depend on MESOS_SANDBOX
        environment.put("MESOS_SANDBOX", System.getenv("MESOS_SANDBOX"));
        Collection<ConfigFileSpecification> configFileSpecifications = getConfigFiles(taskInfo);

        for (ConfigFileSpecification configFileSpecification : configFileSpecifications) {
            TaskUtils.writeConfigFile(
                    configFileSpecification.getRelativePath(),
                    configFileSpecification.getTemplateContent(),
                    environment
            );
        }
    }

    /**
     * Stores the provided config file data in the provided {@link TaskInfo}'s {@code labels} field.
     * Any templates with matching paths will be overwritten.
     *
     * @throws IllegalStateException if the sum total of the provided template content exceeds 100KB
     *                               (102,400B)
     */
    public static TaskInfo.Builder setConfigFiles(
            TaskInfo.Builder taskBuilder, Collection<ConfigFileSpecification> configs)
            throws IllegalStateException {
        int totalSize = 0;
        for (ConfigFileSpecification config : configs) {
            totalSize += config.getTemplateContent().length();
            // Store with the config template prefix:
            taskBuilder.setLabels(TaskUtils.withLabelSet(taskBuilder.getLabels(),
                    CONFIG_TEMPLATE_KEY_PREFIX + config.getRelativePath(),
                    config.getTemplateContent()));
        }
        if (totalSize > CONFIG_TEMPLATE_LIMIT_BYTES) {
            // NOTE: We don't bother checking across multiple set() calls. This is just meant to
            // keep things reasonable without being a perfect check.
            throw new IllegalStateException(String.format(
                    "Provided config template content of %dB across %d files exceeds limit of %dB. "
                            + "Reduce the size of your config templates by at least %dB.",
                    totalSize, configs.size(), CONFIG_TEMPLATE_LIMIT_BYTES,
                    totalSize - CONFIG_TEMPLATE_LIMIT_BYTES));
        }
        return taskBuilder;
    }

    public static GoalState getGoalState(TaskInfo taskInfo) throws TaskException {
        List<String> goalNames = Arrays.stream(GoalState.values())
                .map(goalState -> goalState.name())
                .collect(Collectors.toList());

        Optional<String> goalStateOptional = TaskUtils.findLabelValue(
                taskInfo.getLabels(),
                TaskUtils.GOAL_STATE_KEY);
        if (!goalStateOptional.isPresent()) {
            throw new TaskException("TaskInfo does not contain label with key: " + TaskUtils.GOAL_STATE_KEY);
        }

        String goalStateString = goalStateOptional.get();
        if (!goalNames.contains(goalStateString)) {
            throw new TaskException("Unexpecte goal state encountered: " + goalStateString);
        }

        return GoalState.valueOf(goalStateString);
    }

    public static boolean isTransient(TaskInfo taskInfo) {
        return Boolean.valueOf(getTransientValue(taskInfo));
    }

    private static String getTransientValue(TaskInfo taskInfo) {
        if (taskInfo.hasLabels()) {
            Labels labels = taskInfo.getLabels();
            for (Label label : labels.getLabelsList()) {
                if (label.getKey().equals(TaskUtils.TRANSIENT_FLAG_KEY)) {
                    return label.getValue();
                }
            }
        }

        return null;
    }
}
