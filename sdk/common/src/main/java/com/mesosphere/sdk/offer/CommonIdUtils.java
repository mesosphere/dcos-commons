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
     * Returns the embedded de-slashed service name, or an empty Optional if no service name was found. The returned
     * value will omit any slashes which may have been present in the original service name. Service names are only
     * embedded in task IDs as of SDK 0.50.
     * <p>
     * For example: "pathtoservice__instance-0__aoeu5678" => "pathtoservice" (NOT "/path/to/service"),
     * or "instance-0__aoeu5678" => Empty
     *
     * @see #toTaskId(String)
     */
    public static Optional<String> toSanitizedServiceName(Protos.TaskID taskId) throws TaskException {
        return extractFirstNameFromId(taskId.getValue());
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "pathtoservice__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toTaskId(String)
     */
    public static String toTaskName(Protos.TaskID taskId) throws TaskException {
        return extractLastNameFromId(taskId.getValue());
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "/path/to/service" + "instance-0" => "pathtoservice__instance-0__aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code serviceName} or {@code taskName} contain "__"
     * @see #toTaskName(org.apache.mesos.Protos.TaskID)
     * @see #toSanitizedServiceName(org.apache.mesos.Protos.TaskID)
     */
    public static Protos.TaskID toTaskId(String serviceName, String taskName) throws IllegalArgumentException {
        return Protos.TaskID.newBuilder().setValue(toIdString(serviceName, taskName)).build();
    }

    /**
     * Returns the embedded de-slashed service name, or an empty Optional if no service name was found. The returned
     * value will omit any slashes which may have been present in the original service name. Service names are only
     * embedded in executor IDs as of SDK 0.50.
     * <p>
     * For example: "pathtoservice__instance-0__aoeu5678" => "pathtoservice" (NOT "/path/to/service"),
     * or "instance-0__aoeu5678" => Empty
     *
     * @see #toExecutorId(String)
     */
    public static Optional<String> toSanitizedServiceName(Protos.ExecutorID executorId) throws TaskException {
        return extractFirstNameFromId(executorId.getValue());
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Framework defined executor name.
     * <p>
     * For example: "__path__to__service__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toExecutorId(String)
     */
    public static String toExecutorName(Protos.ExecutorID executorId) throws TaskException {
        return extractLastNameFromId(executorId.getValue());
    }

    /**
     * Converts the Framework defined Executor name into a unique {@link Protos.ExecutorID}.
     * <p>
     * For example: "/path/to/service" + "instance-0" => "__path__to__service__instance-0__aoeu5678"
     *
     * @throws IllegalArgumentException if the provided {@code serviceName} or {@code taskName} contain "__"
     * @see #toExecutorName(org.apache.mesos.Protos.ExecutorID)
     * @see #toSanitizedServiceName(org.apache.mesos.Protos.ExecutorID)
     */
    public static Protos.ExecutorID toExecutorId(String serviceName, String executorName)
            throws IllegalArgumentException {
        return Protos.ExecutorID.newBuilder().setValue(toIdString(serviceName, executorName)).build();
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
     * Returns a sanitized version of the provided service name, suitable for storing in executor/task ids. Note that
     * this sanitized version cannot be directly converted back to the original service name. This utility method is
     * therefore intended to allow comparison between sanitized names.
     */
    public static String toSanitizedServiceName(String serviceName) {
        // Remove any slashes from the service name. Note that certain endpoints already do this, so there's an implicit
        // uniqueness requirement between e.g. "/path/to/service" and "pathtoservice". See EndpointUtils.
        return serviceName.replace("/", "");
    }

    /**
     * Returns a new unique task or executor ID.
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @param itemName the task name or executor name
     * @see #extractFirstNameFromId(String)
     * @see #extractLastNameFromId(String)
     */
    private static String toIdString(String serviceName, String itemName) throws IllegalArgumentException {
        if (serviceName.contains(NAME_ID_DELIM)) {
            throw new IllegalArgumentException(
                    String.format("Service cannot contain delimiter '%s': %s", NAME_ID_DELIM, serviceName));
        }
        if (itemName.contains(NAME_ID_DELIM)) {
            throw new IllegalArgumentException(
                    String.format("Name cannot contain delimiter '%s': %s", NAME_ID_DELIM, itemName));
        }
        // Remove any slashes from the service name. Note that certain endpoints already do this, so there's an implicit
        // uniqueness requirement between e.g. "/path/to/service" and "pathtoservice". See EndpointUtils.
        return toSanitizedServiceName(serviceName) + NAME_ID_DELIM + itemName + NAME_ID_DELIM + UUID.randomUUID();
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
