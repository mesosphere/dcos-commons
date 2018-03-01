package com.mesosphere.sdk.offer;

import java.util.UUID;

import org.apache.mesos.Protos;

/**
 * Various utility methods for manipulating data in {@link Protos.TaskInfo}s.
 */
public class CommonIdUtils {

    /** Used in task and executor IDs to separate the task/executor name from a UUID. */
    private static final String NAME_ID_DELIM = "__";

    private CommonIdUtils() {
        // do not instantiate
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "instance-0__aoeu5678" => "instance-0"
     *
     * @see #toTaskId(String)
     */
    public static String toTaskName(Protos.TaskID taskId) throws TaskException {
        return extractNameFromId(taskId.getValue());
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "instance-0" => "instance-0__aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @see #toTaskName(TaskID)
     */
    public static Protos.TaskID toTaskId(String taskName) throws IllegalArgumentException {
        return Protos.TaskID.newBuilder().setValue(generateIdString(taskName)).build();
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Framework defined executor name.
     * <p>
     * For example: "instance-0_aoeu5678" => "instance-0"
     *
     * @see #toExecutorId(String)
     */
    public static String toExecutorName(Protos.ExecutorID executorId) throws TaskException {
        return extractNameFromId(executorId.getValue());
    }

    /**
     * Converts the Framework defined Executor name into a unique {@link Protos.ExecutorID}.
     * <p>
     * For example: "instance-0" => "instance-0_aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @see #toExecutorName(ExecutorID)
     */
    public static Protos.ExecutorID toExecutorId(String executorName) throws IllegalArgumentException {
        return Protos.ExecutorID.newBuilder().setValue(generateIdString(executorName)).build();
    }

    public static Protos.TaskID emptyTaskId() {
        return Protos.TaskID.newBuilder().setValue("").build();
    }

    public static Protos.SlaveID emptyAgentId() {
        return Protos.SlaveID.newBuilder().setValue("").build();
    }

    public static Protos.ExecutorID emptyExecutorId() {
        return Protos.ExecutorID.newBuilder().setValue("").build();
    }

    /**
     * Returns a new unique task or executor ID.
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @param name the task name or executor name
     * @see #extractNameFromId(String)
     */
    private static String generateIdString(String name) throws IllegalArgumentException {
        if (name.contains(NAME_ID_DELIM)) {
            throw new IllegalArgumentException(
                    String.format("Name cannot contain delimiter '%s': %s", NAME_ID_DELIM, name));
        }
        return name + NAME_ID_DELIM + UUID.randomUUID();
    }

    /**
     * Returns the name component of the provided ID string.
     *
     * @throws TaskException if the provided {@code id} is malformed
     * @see #generateIdString(String)
     */
    private static String extractNameFromId(String id) throws TaskException {
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
