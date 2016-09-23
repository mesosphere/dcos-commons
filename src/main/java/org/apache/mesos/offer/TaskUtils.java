package org.apache.mesos.offer;

import com.google.protobuf.ByteString;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */
public class TaskUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskUtils.class);
    private static final String TARGET_CONFIGURATION_KEY = "target_configuration";
    private static final char TASK_TYPE_DELIM = '-';
    private static final String TASK_NAME_DELIM = "__";
    private static final Pattern TASK_ID_REGEX_FORMAT = Pattern.compile("(.*)-([0-9])+(__.*)");

    /**
     * Label key against which Offer attributes are stored.
     */
    private static final String OFFER_ATTRIBUTES_KEY = "offer_attributes";

    private TaskUtils() {
        // do not instantiate
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     *
     * For example: "instance-0__aoeu5678" => "instance-0"
     */
    public static String toTaskName(TaskID taskId) throws TaskException {
        int underScoreIndex = taskId.getValue().lastIndexOf(TASK_NAME_DELIM);

        if (underScoreIndex == -1) {
            throw new TaskException(String.format(
                    "TaskID '%s' is malformed.  Expected '%s' to extract TaskName from TaskID.  "
                            + "TaskIDs should be generated with TaskUtils.toTaskId().", taskId, TASK_NAME_DELIM));
        }

        return taskId.getValue().substring(0, underScoreIndex);
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     *
     * For example: "instance-0" => "instance-0__aoeu5678"
     */
    public static TaskID toTaskId(String taskName) {
        return TaskID.newBuilder()
                .setValue(taskName + TASK_NAME_DELIM + UUID.randomUUID())
                .build();
    }

    /**
     * Converts the provided task type name and index into a task name.
     *
     * For example: "node" + 0 => "node-0"
     */
    public static String toTaskName(String taskTypeName, int index) {
        return taskTypeName + TASK_TYPE_DELIM + index;
    }

    /**
     * Returns the task type from the provided task id.
     *
     * For example: "node-0__UUID" => "node"
     * @throws IllegalArgumentException if the provided value doesn't match the expected format
     */
    public static String toTaskType(TaskID taskId) throws IllegalArgumentException {
        Matcher matcher = TASK_ID_REGEX_FORMAT.matcher(taskId.getValue());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format(
                    "TaskID value doesn't match expected format %s: %s",
                    TASK_ID_REGEX_FORMAT, taskId));
        }
        return matcher.group(1);
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
     * Returns whether the provided {@link TaskStatus} shows that the task is in a terminated state.
     */
    public static boolean isTerminated(TaskStatus taskStatus) {
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
        Labels.Builder labelsBuilder =
                removeLabel(taskInfo.getLabels(), MesosTask.TRANSIENT_FLAG_KEY).toBuilder();
        labelsBuilder.addLabelsBuilder()
                .setKey(MesosTask.TRANSIENT_FLAG_KEY)
                .setValue("true");

        return TaskInfo.newBuilder(taskInfo)
                .clearLabels()
                .setLabels(labelsBuilder)
                .build();
    }

    /**
     * Ensures that the provided {@link TaskInfo} does not contain a {@link Label} identifying it as
     * a transient task.
     */
    public static TaskInfo clearTransient(TaskInfo taskInfo) {
        return TaskInfo.newBuilder(taskInfo)
                .clearLabels()
                .setLabels(removeLabel(taskInfo.getLabels(), MesosTask.TRANSIENT_FLAG_KEY))
                .build();
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer} into the {@link TaskInfo} as a
     * {@link Label}. Any existing stored attributes are overwritten.
     */
    public static TaskInfo.Builder setOfferAttributes(TaskInfo.Builder taskBuilder, Offer launchOffer) {
        String joinedAttributes = AttributeStringUtils.toString(launchOffer.getAttributesList());

        Labels.Builder labelsBuilder =
                removeLabel(taskBuilder.getLabels(), OFFER_ATTRIBUTES_KEY).toBuilder();
        labelsBuilder.addLabelsBuilder()
                .setKey(OFFER_ATTRIBUTES_KEY)
                .setValue(joinedAttributes);

        return taskBuilder
                .clearLabels()
                .setLabels(labelsBuilder);
    }

    /**
     * Returns the string representations of any {@link Offer} {@link Attribute}s which were
     * embedded in the provided {@link TaskInfo}.
     */
    public static List<String> getOfferAttributeStrings(TaskInfo taskInfo) {
        String joinedAttributes = findLabelValue(taskInfo.getLabels(), OFFER_ATTRIBUTES_KEY);
        if (joinedAttributes == null) {
            return new ArrayList<>();
        }
        return AttributeStringUtils.toStringList(joinedAttributes);
    }

    /**
     * Sets a {@link Label} indicating the target configuruation for the provided {@link TaskInfo}.
     * @param taskInfo is the TaskInfo which will have the appropriate configuration {@link Label} set.
     * @param targetConfigurationId is the ID referencing a particular Configuration in the {@link ConfigStore}
     * @return
     */
    public static TaskInfo setTargetConfiguration(TaskInfo taskInfo, UUID targetConfigurationId) {
        taskInfo = clearTargetConfigurationLabel(taskInfo);
        Labels labels = Labels.newBuilder(taskInfo.getLabels())
                .addLabels(
                        Label.newBuilder()
                                .setKey(TARGET_CONFIGURATION_KEY)
                                .setValue(targetConfigurationId.toString())
                                .build())
                .build();

        return TaskInfo.newBuilder(taskInfo)
                .setLabels(labels)
                .build();
    }

    /**
     * Returns the ID referencing a configuration in a {@link ConfigStore} associated with the provided
     * {@link TaskInfo}.
     * @param taskInfo is the TaskInfo from which the the configuration ID will be extracted.
     * @return the ID of the target configuration for the provided {@link TaskInfo}
     * @throws TaskException when a TaskInfo is provided which does not contain a {@link Label} with an indicated target
     * configuration
     */
    public static UUID getTargetConfiguration(TaskInfo taskInfo) throws TaskException {
        for (Label label : taskInfo.getLabels().getLabelsList()) {
            if (label.getKey().equals(TARGET_CONFIGURATION_KEY)) {
                return UUID.fromString(label.getValue());
            }
        }

        throw new TaskException("TaskInfo does not contain label with key: " + TARGET_CONFIGURATION_KEY);
    }

    private static TaskInfo clearTargetConfigurationLabel(TaskInfo taskInfo) {
        List<Label> filteredLabels = new ArrayList<>();

        for (Label label : taskInfo.getLabels().getLabelsList()) {
            if (!label.getKey().equals(TARGET_CONFIGURATION_KEY)) {
                filteredLabels.add(label);
            }
        }

        return TaskInfo.newBuilder(taskInfo)
                .setLabels(
                        Labels.newBuilder()
                                .addAllLabels(filteredLabels)
                                .build())
                .build();
    }

    private static Labels removeLabel(Labels labels, String key) {
        Labels.Builder labelBuilder = Labels.newBuilder();

        for (Label label : labels.getLabelsList()) {
            if (!label.getKey().equals(key)) {
                labelBuilder.addLabels(label);
            }
        }

        return labelBuilder.build();
    }

    private static String findLabelValue(Labels labels, String key) {
        for (Label label : labels.getLabelsList()) {
            if (label.getKey().equals(key)) {
                return label.getValue();
            }
        }
        return null;
    }

    public static Map<String, String> fromEnvironmentToMap(Protos.Environment environment) {
        Map<String, String> map = new HashMap<>();

        final List<Protos.Environment.Variable> variables = environment.getVariablesList();

        for (Protos.Environment.Variable variable : variables) {
            map.put(variable.getName(), variable.getValue());
        }

        return map;
    }

    public static void sendStatus(ExecutorDriver driver,
                                  Protos.TaskState state,
                                  Protos.TaskID taskID,
                                  Protos.SlaveID slaveID,
                                  Protos.ExecutorID executorID,
                                  String message) {
        sendStatus(driver, state, taskID, slaveID, executorID, message, null);
    }

    public static void sendStatus(ExecutorDriver driver,
                                  Protos.TaskState state,
                                  Protos.TaskID taskID,
                                  Protos.SlaveID slaveID,
                                  Protos.ExecutorID executorID,
                                  String message,
                                  byte[] data) {
        final Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder();

        builder.setState(state);
        builder.setMessage(message);
        builder.setTaskId(taskID);
        builder.setSlaveId(slaveID);
        builder.setExecutorId(executorID);
        builder.setSource(Protos.TaskStatus.Source.SOURCE_EXECUTOR);

        if (data != null) {
            builder.setData(ByteString.copyFrom(data));
        }

        final Protos.TaskStatus taskStatus = builder.build();
        driver.sendStatusUpdate(taskStatus);
    }

    public static boolean areDifferent(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        String oldTaskName = oldTaskSpecification.getName();
        String newTaskName = newTaskSpecification.getName();
        if (!Objects.equals(oldTaskName, newTaskName)) {
            LOGGER.info(String.format("Task names '%s' and '%s' are different.", oldTaskName, newTaskName));
            return true;
        }

        CommandInfo oldCommand = oldTaskSpecification.getCommand();
        CommandInfo newCommand = newTaskSpecification.getCommand();
        if (!Objects.equals(oldCommand, newCommand)) {
            LOGGER.info(String.format("Task commands '%s' and '%s' are different.", oldCommand, newCommand));
            return true;
        }

        Map<String, ResourceSpecification> oldResourceMap = getResourceSpecMap(oldTaskSpecification.getResources());
        Map<String, ResourceSpecification> newResourceMap = getResourceSpecMap(newTaskSpecification.getResources());

        if (oldResourceMap.size() != newResourceMap.size()) {
            LOGGER.info(String.format("Resource lengths are different for old resources: '%s' and new resources: '%s'",
                    oldResourceMap, newResourceMap));
            return true;
        }

        for (Map.Entry<String, ResourceSpecification> newEntry : newResourceMap.entrySet()) {
            String resourceName = newEntry.getKey();
            LOGGER.info("Checking resource difference for: " + resourceName);
            ResourceSpecification oldResourceSpec = oldResourceMap.get(resourceName);
            if (oldResourceSpec == null) {
                LOGGER.info("Resource not found: " + resourceName);
                return true;
            } else if (ResourceUtils.areDifferent(oldResourceSpec, newEntry.getValue())) {
                LOGGER.info("Resources are different.");
                return true;
            }
        }

        return false;
    }

    private static Map<String, ResourceSpecification> getResourceSpecMap(
            Collection<ResourceSpecification> resourceSpecifications) {
        Map<String, ResourceSpecification> resourceMap = new HashMap<>();
        for (ResourceSpecification resourceSpecification : resourceSpecifications) {
            resourceMap.put(resourceSpecification.getName(), resourceSpecification);
        }

        return resourceMap;
    }
}
