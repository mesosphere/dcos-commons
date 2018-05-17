package com.mesosphere.sdk.testing;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;

/**
 * Groups information about a launch operation, including the executor, task(s), and reservations.
 */
public class LaunchedPod {
    private final Protos.ExecutorInfo executorInfo;
    private final Collection<Protos.TaskInfo> taskInfos;
    private final Collection<Protos.Resource> reservations;

    public LaunchedPod(
            Protos.ExecutorInfo executorInfo,
            Collection<Protos.TaskInfo> taskInfos,
            Collection<Protos.Resource> reservations) {
        this.executorInfo = executorInfo;
        this.taskInfos = taskInfos;
        this.reservations = reservations;
    }

    public Protos.ExecutorInfo getExecutor() {
        return executorInfo;
    }

    public Collection<Protos.TaskInfo> getTasks() {
        return taskInfos;
    }

    public Collection<Protos.Resource> getReservations() {
        return reservations;
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
