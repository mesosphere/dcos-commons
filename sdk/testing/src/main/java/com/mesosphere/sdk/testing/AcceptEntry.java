package com.mesosphere.sdk.testing;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;

/**
 * Groups information about a set of operations sent to Mesos in an {@code acceptOffers()} call.
 *
 * This includes any executor(s) and task(s) that were launched, along with any reservations that were created.
 */
public class AcceptEntry {
    private final Collection<Protos.ExecutorInfo> executorInfos;
    private final Collection<Protos.TaskInfo> taskInfos;
    private final Collection<Protos.Resource> reservations;

    public AcceptEntry(
            Collection<Protos.ExecutorInfo> executorInfos,
            Collection<Protos.TaskInfo> taskInfos,
            Collection<Protos.Resource> reservations) {
        this.executorInfos = executorInfos;
        this.taskInfos = taskInfos;
        this.reservations = reservations;
    }

    public Collection<Protos.ExecutorInfo> getExecutors() {
        return executorInfos;
    }

    public Collection<Protos.TaskInfo> getTasks() {
        return taskInfos;
    }

    public Collection<Protos.Resource> getReservations() {
        return reservations;
    }

    @Override
    public String toString() {
        return String.format("Executors: %s%nTasks: %s%nReservations: %s",
                executorInfos.stream()
                        .map(e -> TextFormat.shortDebugString(e))
                        .collect(Collectors.toList()),
                taskInfos.stream()
                        .map(t -> TextFormat.shortDebugString(t))
                        .collect(Collectors.toList()),
                reservations.stream()
                        .map(r -> TextFormat.shortDebugString(r))
                        .collect(Collectors.toList()));
    }
}
