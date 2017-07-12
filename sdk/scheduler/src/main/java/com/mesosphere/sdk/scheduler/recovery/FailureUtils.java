package com.mesosphere.sdk.scheduler.recovery;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {

    /**
     * Check if a Task has been marked as permanently failed.
     *
     * @param taskInfo The Task to check for failure.
     * @return True if the Task has been marked, false otherwise.
     */
    public static boolean isLabeledAsFailed(Protos.TaskInfo taskInfo) {
        return new TaskLabelReader(taskInfo).isPermanentlyFailed();
    }

    public static boolean isLabeledAsFailed(PodInstance podInstance, StateStore stateStore) {
        Collection<Protos.TaskInfo> taskInfos = getTaskInfos(podInstance, stateStore);
        if (taskInfos.isEmpty()) {
            return false;
        }

        return taskInfos.stream().allMatch(taskInfo -> isLabeledAsFailed(taskInfo));
    }

    /**
     * Mark a task as permanently failed.  This new marked Task should be persistently stored.
     *
     * @param taskInfo The Task to be marked.
     * @return The marked TaskInfo which may be a copy of the original TaskInfo.
     */
    @VisibleForTesting
    public static Protos.TaskInfo markFailed(Protos.TaskInfo taskInfo) {
        return taskInfo.toBuilder()
                .setLabels(new TaskLabelWriter(taskInfo)
                        .setPermanentlyFailed()
                        .toProto())
                .build();
    }

    public static void markFailed(PodInstance podInstance, StateStore stateStore) {
        stateStore.storeTasks(
                getTaskInfos(podInstance, stateStore).stream()
                        .map(taskInfo -> FailureUtils.markFailed(taskInfo))
                        .collect(Collectors.toList()));
    }

    /**
     * Remove the permanently failed label, if any, from the TaskInfo.
     */
    @VisibleForTesting
    static Protos.TaskInfo clearFailed(Protos.TaskInfo taskInfo) {
        return taskInfo.toBuilder()
                .setLabels(new TaskLabelWriter(taskInfo)
                        .clearPermanentlyFailed()
                        .toProto())
                .build();
    }

    public static Collection<Protos.TaskInfo> clearFailed(PodInstance podInstance, StateStore stateStore) {
        return getTaskInfos(podInstance, stateStore).stream()
                .map(taskInfo -> FailureUtils.clearFailed(taskInfo))
                .collect(Collectors.toList());
    }

    private static Collection<Protos.TaskInfo> getTaskInfos(PodInstance podInstance, StateStore stateStore) {
        Collection<String> taskInfoNames = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        return taskInfoNames.stream()
                .map(name -> stateStore.fetchTask(name))
                .filter(taskInfo -> taskInfo.isPresent())
                .map(taskInfo -> taskInfo.get())
                .collect(Collectors.toList());
    }
}
