package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

/**
 * Utility methods relating to sending {@link TaskStatus} messages from the Executor back to the Scheduler.
 */
public class TaskStatusUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusUtils.class);

    private TaskStatusUtils() {
        // do not instantiate
    }

    /**
     * Invokes {@link #sendStatus(ExecutorDriver, TaskState, TaskID, SlaveID, ExecutorID, String, boolean)} with null
     * {@code labels} and {@code data} values.
     */
    public static void sendStatus(ExecutorDriver driver,
                                  TaskState state,
                                  TaskID taskID,
                                  SlaveID slaveID,
                                  ExecutorID executorID,
                                  String message,
                                  boolean isHealthy) {
        sendStatus(driver, state, taskID, slaveID, executorID, message, isHealthy, null, null);
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
                                  boolean isHealthy,
                                  Labels labels,
                                  byte[] data) {
        final TaskStatus.Builder builder = TaskStatus.newBuilder();

        builder.setState(state);
        builder.setMessage(message);
        builder.setTaskId(taskID);
        builder.setSlaveId(slaveID);
        builder.setExecutorId(executorID);
        builder.setSource(TaskStatus.Source.SOURCE_EXECUTOR);
        builder.setHealthy(isHealthy);

        if (data != null) {
            builder.setData(ByteString.copyFrom(data));
        }

        if (labels != null) {
            builder.setLabels(labels);
        }

        try {
            final TaskStatus taskStatus = builder.build();
            driver.sendStatusUpdate(taskStatus);
        } catch (Throwable t) {
            LOGGER.info("Failed to build task status.", t);
        }
    }

}
