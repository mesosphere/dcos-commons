package com.mesosphere.sdk.testing;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;

/**
 * Grouping of a set of tasks that were launched in a pod, along with their associated executor.
 */
public class LaunchedPod {
    private final Protos.ExecutorInfo executorInfo;
    private final Collection<Protos.TaskInfo> taskInfos;

    public LaunchedPod(Protos.ExecutorInfo executorInfo, Collection<Protos.TaskInfo> taskInfos) {
        this.executorInfo = executorInfo;
        this.taskInfos = taskInfos;
    }

    public Protos.ExecutorInfo getExecutor() {
        return executorInfo;
    }

    public Collection<Protos.TaskInfo> getTasks() {
        return taskInfos;
    }

    @Override
    public String toString() {
        return String.format("Executor: %s%nTasks: %s",
                TextFormat.shortDebugString(executorInfo),
                taskInfos.stream()
                        .map(t -> TextFormat.shortDebugString(t))
                        .collect(Collectors.toList()));
    }
}
