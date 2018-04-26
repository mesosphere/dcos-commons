package com.mesosphere.sdk.offer;

import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;

/**
 * Various utility methods for manipulating data in {@link Protos.TaskInfo}s.
 */
public class CommonIdUtils {

    /**
     * Used in task and executor IDs to separate the task/executor name from a UUID.
     */
    private static final String NAME_ID_DELIM = "__";

    /**
     * Mesos disallows slashes in task/executor ids. We therefore need to sanitize service names before embedding them
     * in task/executor ids.
     */
    private static final String SANITIZE_ID_FROM = "/";

    /**
     * As a part of sanitizing task/executor ids, we replace any slashes with dots.
     */
    private static final String SANITIZE_ID_TO = ".";

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
     * For example: "path.to.service__instance-0__aoeu5678" => "path.to.service" (NOT "/path/to/service"),
     * or "instance-0__aoeu5678" => Empty
     *
     * @see #toTaskId(String)
     */
    public static Optional<String> toSanitizedServiceName(Protos.TaskID taskId) throws TaskException {
        return extractServiceNameFromId(taskId.getValue());
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "path.to.service__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toTaskId(String)
     */
    public static String toTaskName(Protos.TaskID taskId) throws TaskException {
        return extractTaskNameFromId(taskId.getValue());
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "/path/to/service" + "instance-0" => "path.to.service__instance-0__aoeu5678"
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
     * For example: "path.to.service__instance-0__aoeu5678" => "path.to.service" (NOT "/path/to/service"),
     * or "instance-0__aoeu5678" => Empty
     *
     * @see #toExecutorId(String)
     */
    public static Optional<String> toSanitizedServiceName(Protos.ExecutorID executorId) throws TaskException {
        return extractServiceNameFromId(executorId.getValue());
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Framework defined executor name.
     * <p>
     * For example: "path.to.service__instance-0__aoeu5678" => "instance-0"
     *
     * @see #toExecutorId(String)
     */
    public static String toExecutorName(Protos.ExecutorID executorId) throws TaskException {
        return extractTaskNameFromId(executorId.getValue());
    }

    /**
     * Converts the Framework defined Executor name into a unique {@link Protos.ExecutorID}.
     * <p>
     * For example: "/path/to/service" + "instance-0" => "path.to.service__instance-0__aoeu5678"
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
     * therefore make public to allow comparison between sanitized names as needed.
     * <p>
     * For example: {@code /path/to/service} => {@code path.to.service}
     */
    public static String toSanitizedServiceName(String serviceName) {
        // Remove any leading/trailing slashes. Replace any other slashes with dots.
        return StringUtils.strip(serviceName, SANITIZE_ID_FROM).replace(SANITIZE_ID_FROM, SANITIZE_ID_TO);
    }

    /**
     * Returns a new unique task or executor ID.
     *
     * @throws IllegalArgumentException if the provided {@code name} contains the "__" delimiter
     * @param itemName the task name or executor name
     * @see #extractServiceNameFromId(String)
     * @see #extractTaskNameFromId(String)
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
    private static Optional<String> extractServiceNameFromId(String id) throws TaskException {
        return Optional.ofNullable(extract(id, true));
    }

    /**
     * Returns the last name component of the provided ID string.
     *
     * @throws TaskException if the provided {@code id} is malformed
     * @see #generateIdString(String)
     */
    private static String extractTaskNameFromId(String id) throws TaskException {
        return extract(id, false);
    }

    /**
     * Extracts either the service name or task name from the provided {@code id} string.
     *
     * @param id the string to extract from
     * @param serviceName whether to return a service name ({@code true}) or task name ({@code false})
     * @return the service name or task name. if {@code serviceName} is {@code true} then this may return {@code null}
     * @throws TaskException if the {@code id} is malformed
     */
    private static String extract(String id, boolean serviceName) throws TaskException {
        // Support both of the following (note double underscores as delimiter):
        // - "task-name__uuid"
        // - "service-name__task-name__uuid"
        int lastDelimIndex = id.lastIndexOf(NAME_ID_DELIM);
        if (lastDelimIndex == -1) {
            throw new TaskException(String.format(
                    "ID '%s' is malformed. Expected '%s' to extract name from ID. "
                    + "IDs should be generated with CommonIdUtils.",
                    id, NAME_ID_DELIM));
        }
        if (!serviceName) {
            // Return "task-name" preceding "uuid"
            return seekBackFrom(id, lastDelimIndex);
        }

        // Return "service-name" preceding "task-name", or null if it's a short id
        int secondLastDelimIndex = id.lastIndexOf(NAME_ID_DELIM, lastDelimIndex - NAME_ID_DELIM.length());
        if (secondLastDelimIndex == -1) {
            // Only one set of double underscores: "task-name__uuid"
            return null;
        }
        // Two or more sets of double underscores: "service-name__task-name__uuid" or
        // "other-info__service-name__task-name__uuid" (for future expansion)
        return seekBackFrom(id, secondLastDelimIndex);
    }

    /**
     * Seeks back from {@code endIndex} to find a prior {@code NAME_ID_DELIM} and returns that range, or returns the
     * whole string until {@code endIndex} if no prior {@code NAME_ID_DELIM} is found.
     */
    private static String seekBackFrom(String id, int endIndex) {
        int beginIndex = id.lastIndexOf(NAME_ID_DELIM, endIndex - NAME_ID_DELIM.length());
        if (beginIndex == -1) {
            // No earlier delim found, return everything before endIndex
            return id.substring(0, endIndex);
        } else {
            // Earlier delim found, return range from that delim until endIndex
            return id.substring(beginIndex + NAME_ID_DELIM.length(), endIndex);
        }
    }
}
