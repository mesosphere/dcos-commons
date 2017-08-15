package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;

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
    public static boolean isPermanentlyFailed(Protos.TaskInfo taskInfo) {
        return new TaskLabelReader(taskInfo).isPermanentlyFailed();
    }

    /**
     * Marks all tasks associated with this pod as failed.
     * This setting will effectively be automatically cleared when the pod is redeployed.
     *
     * @param stateStore the state storage where any updated tasks will be stored
     * @param podInstance the pod whose tasks will be marked as failed
     */
    public static void setPermanentlyFailed(StateStore stateStore, PodInstance podInstance) {
        stateStore.storeTasks(
                StateStoreUtils.fetchPodTasks(stateStore, podInstance).stream()
                        .map(taskInfo -> taskInfo.toBuilder()
                                .setLabels(new TaskLabelWriter(taskInfo).setPermanentlyFailed().toProto())
                                .build())
                        .collect(Collectors.toList()));
    }
}
