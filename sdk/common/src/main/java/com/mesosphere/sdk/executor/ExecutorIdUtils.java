package com.mesosphere.sdk.executor;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.TaskException;

import java.util.UUID;

/**
 * Various utility methods for manipulating data in {@link org.apache.mesos.Protos.ExecutorInfo}s.
 */
public class ExecutorIdUtils {
    private static final String EXECUTOR_NAME_DELIM = "__";

    private ExecutorIdUtils() {
        // do not instantiate
    }

    /**
     * Converts the unique {@link Protos.ExecutorID} into a Framework defined executor name.
     *
     * For example: "instance-0_aoeu5678" => "instance-0"
     */
    public static String toExecutorName(Protos.ExecutorID executorId) throws TaskException {
      int underScoreIndex = executorId.getValue().lastIndexOf(EXECUTOR_NAME_DELIM);

      if (underScoreIndex == -1) {
        throw new TaskException(String.format(
                "ExecutorID '%s' is malformed.  Expected '%s' to extract ExecutorName from ExecutorID.  "
                + "ExecutorIDs should be generated with ExecutorUtils.toExecutorId().",
                executorId, EXECUTOR_NAME_DELIM));
      }

      return executorId.getValue().substring(0, underScoreIndex);
    }

    /**
     * Converts the Framework defined Executor name into a unique {@link Protos.ExecutorID}.
     *
     * For example: "instance-0" => "instance-0_aoeu5678"
     */
    public static Protos.ExecutorID toExecutorId(String executorName) {
        return Protos.ExecutorID.newBuilder()
                .setValue(executorName + EXECUTOR_NAME_DELIM + UUID.randomUUID())
                .build();
    }
}
