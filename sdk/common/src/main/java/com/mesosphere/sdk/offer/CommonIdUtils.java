package com.mesosphere.sdk.offer;

import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos;

/**
 * Various utility methods for manipulating data in {@link Protos.TaskInfo}s.
 */
public class CommonIdUtils {

    /**
     * Used in task and executor IDs to separate the task/executor name from a UUID.
     */
    private static final String NAME_ID_DELIM = "__";

    private static final Protos.TaskID EMPTY_TASK_ID = Protos.TaskID.newBuilder().setValue("").build();
    private static final Protos.SlaveID EMPTY_AGENT_ID = Protos.SlaveID.newBuilder().setValue("").build();

    private CommonIdUtils() {
        // do not instantiate
    }

    /**
     * Converts the unique {@link TaskID} into a Service name.
     * <p>
     * For example: "service__instance-0__aoeu5678" => "instance-0", or "instance-0__aoeu5678" => Empty
     *
     * @see #toTaskId(String)
     */
    public static Optional<String> toServiceName(Protos.TaskID taskId) throws TaskException {
        return extractFirstNameFromId(taskId.getValue());
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "service__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toTaskId(String)
     */
    public static String toTaskName(Protos.TaskID taskId) throws TaskException {
        return extractLastNameFromId(taskId.getValue());
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "instance-0" => "instance-0__aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @see #toTaskName(TaskID)
     */
    public static Protos.TaskID toTaskId(String serviceName, String taskName) throws IllegalArgumentException {
        return Protos.TaskID.newBuilder().setValue(generateIdString(serviceName, taskName)).build();
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Service name.
     * <p>
     * For example: "service__instance-0__aoeu5678" => "service", or "instance-0__aoeu5678" => Empty
     *
     * @see #toExecutorId(String)
     */
    public static Optional<String> toServiceName(Protos.ExecutorID executorId) throws TaskException {
        return extractFirstNameFromId(executorId.getValue());
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Framework defined executor name.
     * <p>
     * For example: "service__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toExecutorId(String)
     */
    public static String toExecutorName(Protos.ExecutorID executorId) throws TaskException {
        return extractLastNameFromId(executorId.getValue());
    }

    /**
     * Converts the Framework defined Executor name into a unique {@link Protos.ExecutorID}.
     * <p>
     * For example: "instance-0" => "instance-0_aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @see #toExecutorName(ExecutorID)
     */
    public static Protos.ExecutorID toExecutorId(String serviceName, String executorName)
            throws IllegalArgumentException {
        return Protos.ExecutorID.newBuilder().setValue(generateIdString(serviceName, executorName)).build();
    }

    /**
     * Returns a Task ID whose value is an empty string.
     */
    public static Protos.TaskID emptyTaskId() {
        return EMPTY_TASK_ID;
    }

    /**
     * Returns an Agent ID whose value is an empty string.
     */
    public static Protos.SlaveID emptyAgentId() {
        return EMPTY_AGENT_ID;
    }

    /**
     * Returns a new unique task or executor ID.
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @param itemName the task name or executor name
     * @see #extractFirstNameFromId(String)
     * @see #extractLastNameFromId(String)
     */
    private static String generateIdString(String serviceName, String itemName) throws IllegalArgumentException {
        if (serviceName.contains(NAME_ID_DELIM)) {
            throw new IllegalArgumentException(
                    String.format("Service cannot contain delimiter '%s': %s", NAME_ID_DELIM, serviceName));
        }
        if (itemName.contains(NAME_ID_DELIM)) {
            throw new IllegalArgumentException(
                    String.format("Name cannot contain delimiter '%s': %s", NAME_ID_DELIM, itemName));
        }
        return serviceName + NAME_ID_DELIM + itemName + NAME_ID_DELIM + UUID.randomUUID();
    }

    /**
     * Returns the first name component of the provided ID string.
     *
     * @throws TaskException if the provided {@code id} is malformed
     * @see #generateIdString(String)
     */
    private static Optional<String> extractFirstNameFromId(String id) throws TaskException {
        // Support both of the following (note double underscores as delimiter):
        // - "task_name__uuid"
        // - "other_info__task_name__uuid"
        int lastIndex = id.lastIndexOf(NAME_ID_DELIM);
        if (lastIndex == -1) {
            throw new TaskException(String.format(
                    "ID '%s' is malformed. Expected '%s' to extract name from ID. "
                    + "IDs should be generated with CommonIdUtils.",
                    id, NAME_ID_DELIM));
        }
        int secondLastIndex = id.lastIndexOf(NAME_ID_DELIM, lastIndex - NAME_ID_DELIM.length());
        if (secondLastIndex == -1) {
            // Only one set of double underscores: "task_name__uuid"
            return Optional.empty();
        } else {
            // Two sets of double underscores: "service_name__task_name__uuid"
            return Optional.of(id.substring(0, secondLastIndex));
        }
    }

    /**
     * Returns the last name component of the provided ID string.
     *
     * @throws TaskException if the provided {@code id} is malformed
     * @see #generateIdString(String)
     */
    private static String extractLastNameFromId(String id) throws TaskException {
        // Support both of the following (note double underscores as delimiter):
        // - "task_name__uuid"
        // - "other_info__task_name__uuid"
        int lastIndex = id.lastIndexOf(NAME_ID_DELIM);
        if (lastIndex == -1) {
            throw new TaskException(String.format(
                    "ID '%s' is malformed. Expected '%s' to extract name from ID. "
                    + "IDs should be generated with CommonIdUtils.",
                    id, NAME_ID_DELIM));
        }
        int secondLastIndex = id.lastIndexOf(NAME_ID_DELIM, lastIndex - NAME_ID_DELIM.length());
        if (secondLastIndex == -1) {
            // Only one set of double underscores: "task_name__uuid"
            return id.substring(0, lastIndex);
        } else {
            // Two sets of double underscores: "other_name__task_name__uuid"
            return id.substring(secondLastIndex + NAME_ID_DELIM.length(), lastIndex);
        }
    }
}
