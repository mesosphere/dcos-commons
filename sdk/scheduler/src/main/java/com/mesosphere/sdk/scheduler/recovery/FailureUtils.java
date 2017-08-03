package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {

    /**
     * Indicator of the type of launch to be performed for a task.
     */
    public enum LaunchType {
        UNKNOWN,

        /**
         * This is the first time the task has been launched at this location.
         * Scenarios: initial deployment of the service, or following a 'pod replace' operation.
         */
        INITIAL_LAUNCH,

        /**
         * The task has been successfully launched at this location before (i.e. reached goal state), and it is now
         * being relaunched at the same location.
         * Scenarios: automatic restart due to exited task, or following a 'pod restart' operation.
         */
        RELAUNCH
    }

    private static final String INITIAL_LAUNCH_STATUS_LABEL = "initial_launch";

    private FailureUtils() {
        // do not instantiate
    }

    /**
     * Check if a Task's status indicates that it's the initial launch of that task.
     */
    public static boolean isInitialLaunch(Protos.TaskStatus status) {
        if (status.getState() != Protos.TaskState.TASK_STAGING) {
            return false;
        }

        for (Protos.Label l : status.getLabels().getLabelsList()) { // TODO(nickbp): remove manual access
            if (l.getKey().equals(INITIAL_LAUNCH_STATUS_LABEL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given a {@link Protos.TaskInfo} representing the prior launch of a task (or an empty Optional if no prior version
     * exists), returns the type of launch to be performed.
     */
    public static FailureUtils.LaunchType getLaunchType(Optional<Protos.TaskInfo> priorTaskInfo) {
        if (!priorTaskInfo.isPresent()) {
            // This task has never been launched anywhere, otherwise the prior TaskInfo would have something
            return FailureUtils.LaunchType.INITIAL_LAUNCH;
        }

        if (new TaskLabelReader(priorTaskInfo.get()).isPermanentlyFailed()) {
            // This task had been launched somewhere before, but now it's being relaunched from scratch
            return FailureUtils.LaunchType.INITIAL_LAUNCH;
        }

        return FailureUtils.LaunchType.RELAUNCH;
    }

    /**
     * Check if a Task has been marked as permanently failed.
     *
     * @param taskInfo The Task to check for failure.
     * @return True if the Task has been marked, false otherwise.
     */
    public static boolean isMarkedFailed(Protos.TaskInfo taskInfo) {
        return new TaskLabelReader(taskInfo).isPermanentlyFailed();
    }

    /**
     * Returns whether all present tasks in the provided pod have been marked as permanently failed in the provided
     * state store. If no tasks are present, or if any present tasks are not marked failed, then this returns
     * {@code false}.
     */
    public static boolean isAllMarkedFailed(StateStore stateStore, PodInstance podInstance) {
        Collection<Protos.TaskInfo> taskInfos = StateStoreUtils.fetchPodTasks(stateStore, podInstance);
        if (taskInfos.isEmpty()) {
            return false;
        }
        return taskInfos.stream().allMatch(taskInfo -> isMarkedFailed(taskInfo));
    }

    /**
     * Marks all tasks associated with this pod as failed.
     * These will effectively be automatically cleared when the pod is redeployed.
     */
    public static void markFailed(StateStore stateStore, PodInstance podInstance) {
        stateStore.storeTasks(
                StateStoreUtils.fetchPodTasks(stateStore, podInstance).stream()
                        .map(taskInfo -> taskInfo.toBuilder()
                                .setLabels(new TaskLabelWriter(taskInfo)
                                        .setPermanentlyFailed()
                                        .toProto())
                                .build())
                        .collect(Collectors.toList()));
    }
}
