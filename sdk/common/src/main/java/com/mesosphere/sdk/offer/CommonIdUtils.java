package com.mesosphere.sdk.offer;

import java.util.UUID;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;

/**
 * Various utility methods for manipulating data in {@link Protos.TaskInfo}s.
 */
public class CommonIdUtils {

    /** Used in task and executor IDs to separate the task/executor name from a UUID. */
    public static final String NAME_ID_DELIM = "__";

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
    public static String toTaskName(TaskID taskId) throws TaskException {
        return extractNameFromId(taskId.getValue());
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "instance-0" => "instance-0__aoeu5678"
     *
     * @see #toTaskName(TaskID)
     */
    public static TaskID toTaskId(String taskName) {
        return TaskID.newBuilder().setValue(generateIdString(taskName)).build();
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
     * @see #toExecutorName(ExecutorID)
     */
    public static Protos.ExecutorID toExecutorId(String executorName) {
        return Protos.ExecutorID.newBuilder().setValue(generateIdString(executorName)).build();
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
     * Returns a new unique task or executor ID.
     *
     * @param name the task name or executor name
     * @see #extractNameFromId(String)
     */
    private static String generateIdString(String name) {
        return name + NAME_ID_DELIM + UUID.randomUUID();
    }

    /**
     * Returns the name component of the provided ID string.
     *
     * @throws TaskException if the provided {@code id} is malformed
     * @see #generateIdString(String)
     */
    private static String extractNameFromId(String id) throws TaskException {
        int underScoreIndex = id.lastIndexOf(NAME_ID_DELIM);
        if (underScoreIndex == -1) {
          throw new TaskException(String.format(
                  "ID '%s' is malformed.  Expected '%s' to extract name from ID.  "
                  + "IDs should be generated with CommonIdUtils.",
                  id, NAME_ID_DELIM));
        }
        return id.substring(0, underScoreIndex);
    }
}
