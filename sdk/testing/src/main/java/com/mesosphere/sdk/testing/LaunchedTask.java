package com.mesosphere.sdk.testing;

import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;

/**
 * Grouping of a launched task along with its associated executor.
 */
public class LaunchedTask {
    private final Protos.ExecutorInfo executorInfo;
    private final Protos.TaskInfo taskInfo;

    public LaunchedTask(Protos.ExecutorInfo executorInfo, Protos.TaskInfo taskInfo) {
        this.executorInfo = executorInfo;
        this.taskInfo = taskInfo;
    }

    public Protos.ExecutorInfo getExecutor() {
        return executorInfo;
    }

    public Protos.TaskInfo getTask() {
        return taskInfo;
    }

    @Override
    public String toString() {
        return String.format("Executor: %s%nTask: %s",
                TextFormat.shortDebugString(executorInfo), TextFormat.shortDebugString(taskInfo));
    }
}
