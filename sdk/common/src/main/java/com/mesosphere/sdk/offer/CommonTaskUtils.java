package com.mesosphere.sdk.offer;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.DefaultConfigFileSpec;
import com.mesosphere.sdk.specification.GoalState;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.*;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */
public class CommonTaskUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTaskUtils.class);

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

    private CommonTaskUtils() {
        // do not instantiate
    }

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
     * Ensures that the provided {@link org.apache.mesos.Protos.TaskInfo.Builder} contains a {@link Label} identifying
     * it as a transient task.
     */
    public static TaskInfo.Builder setTransient(TaskInfo.Builder taskInfo) {
        return taskInfo
                .setLabels(withLabelSet(taskInfo.getLabels(),
                        TRANSIENT_FLAG_KEY,
                        "true"));
    }

    /**
     * Ensures that the provided {@link org.apache.mesos.Protos.TaskInfo.Builder} does not contain a {@link Label}
     * identifying it as a transient task.
     */
    public static TaskInfo.Builder clearTransient(TaskInfo.Builder builder) {
        return builder.setLabels(withLabelRemoved(builder.getLabels(), TRANSIENT_FLAG_KEY));
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
     * Stores the provided config file data in the provided {@link TaskInfo}'s {@code labels} field.
     * Any templates with matching paths will be overwritten.
     *
     * @throws IllegalStateException if the sum total of the provided template content exceeds 100KB
     *                               (102,400B)
     */
    public static TaskInfo.Builder setConfigFiles(
            TaskInfo.Builder taskBuilder, Collection<ConfigFileSpec> configs)
            throws IllegalStateException {
        int totalSize = 0;
        for (ConfigFileSpec config : configs) {
            totalSize += config.getTemplateContent().length();
            // Store with the config template prefix:
            taskBuilder.setLabels(CommonTaskUtils.withLabelSet(taskBuilder.getLabels(),
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

    /**
     * Retrieves the config file data, if any, from the provided {@link TaskInfo}'s {@code labels}
     * field. If no data is found, returns an empty collection.
     */
    public static Collection<ConfigFileSpec> getConfigFiles(TaskInfo taskInfo)
            throws InvalidProtocolBufferException {
        List<ConfigFileSpec> configs = new ArrayList<>();
        for (Label label : taskInfo.getLabels().getLabelsList()) {
            // Extract all labels whose key has the expected prefix:
            if (!label.getKey().startsWith(CONFIG_TEMPLATE_KEY_PREFIX)) {
                continue;
            }
            configs.add(new DefaultConfigFileSpec(
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
    public static Environment.Builder fromMapToEnvironment(Map<String, String> environmentMap) {
        if (environmentMap == null) {
            return Environment.newBuilder();
        }

        Collection<Environment.Variable> vars = environmentMap
                .entrySet()
                .stream()
                .map(entrySet -> Environment.Variable.newBuilder()
                        .setName(entrySet.getKey())
                        .setValue(entrySet.getValue()).build())
                .collect(Collectors.toList());

        return Environment.newBuilder().addAllVariables(vars);
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

    public static ProcessBuilder getProcess(TaskInfo taskInfo) {
        CommandInfo commandInfo = taskInfo.getCommand();
        String cmd = commandInfo.getValue();

        ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", cmd);
        builder.inheritIO();
        builder.environment().putAll(CommonTaskUtils.fromEnvironmentToMap(commandInfo.getEnvironment()));

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

    public static GoalState getGoalState(TaskInfo taskInfo) throws TaskException {
        List<String> goalNames = Arrays.stream(GoalState.values())
                .map(goalState -> goalState.name())
                .collect(Collectors.toList());

        Optional<String> goalStateOptional = CommonTaskUtils.findLabelValue(
                taskInfo.getLabels(),
                GOAL_STATE_KEY);
        if (!goalStateOptional.isPresent()) {
            throw new TaskException("TaskInfo does not contain label with key: " + GOAL_STATE_KEY);
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
                if (label.getKey().equals(TRANSIENT_FLAG_KEY)) {
                    return label.getValue();
                }
            }
        }

        return null;
    }
}
