package org.apache.mesos.executor;

import org.apache.mesos.Protos;

import java.util.UUID;

/**
 * Various utility methods for manipulating data in {@link org.apache.mesos.Protos.ExecutorInfo}s.
 */
public class ExecutorUtils {
    private static final String EXECUTOR_NAME_DELIM = "__";

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
