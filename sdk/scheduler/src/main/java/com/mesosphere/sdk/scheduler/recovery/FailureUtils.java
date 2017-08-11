package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {

    private FailureUtils() {
        // do not instantiate
    }

    /**
     * Check if a Task has been marked as permanently failed.
     *
     * @param taskInfo The Task to check for failure.
     * @return True if the Task has been marked, false otherwise.
     */
    public static boolean isLabeledAsFailed(Protos.TaskInfo taskInfo) {
        return new TaskLabelReader(taskInfo).isPermanentlyFailed();
    }

    /**
     * Returns whether <i>all</i> present tasks in the provided pod have been marked as permanently failed in the
     * provided state store. If no tasks are present, or if any present tasks are not marked failed, then this returns
     * {@code false}.
     */
    public static boolean isAllLabeledAsFailed(StateStore stateStore, PodInstance podInstance) {
        Collection<Protos.TaskInfo> taskInfos = StateStoreUtils.fetchPodTasks(stateStore, podInstance);
        if (taskInfos.isEmpty()) {
            return false;
        }
        return taskInfos.stream().allMatch(taskInfo -> isLabeledAsFailed(taskInfo));
    }

    /**
     * Marks all tasks associated with this pod as failed.
     * These will effectively be automatically cleared when the pod is redeployed.
     */
    public static void markFailed(StateStore stateStore, PodInstance podInstance) {
        stateStore.storeTasks(
                StateStoreUtils.fetchPodTasks(stateStore, podInstance).stream()
                        .map(taskInfo -> taskInfo.toBuilder()
                                .setLabels(new TaskLabelWriter(taskInfo).setPermanentlyFailed().toProto())
                                .build())
                        .collect(Collectors.toList()));
    }
}
