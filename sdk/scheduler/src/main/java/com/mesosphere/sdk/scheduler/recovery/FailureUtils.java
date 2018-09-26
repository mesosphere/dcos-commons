package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.http.types.GroupedTasks;
import com.mesosphere.sdk.http.types.TaskInfoAndStatus;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {

    private static final Logger LOGGER = LoggingUtils.getLogger(FailureUtils.class);

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
        setPermanentlyFailed(stateStore, TaskUtils.getPodTasks(podInstance, stateStore.fetchTasks()));
    }

    public static void setPermanentlyFailed(StateStore stateStore, Collection<Protos.TaskInfo> failedTasks) {
        stateStore.storeTasks(failedTasks.stream()
                .map(taskInfo -> taskInfo.toBuilder()
                        .setLabels(new TaskLabelWriter(taskInfo).setPermanentlyFailed().toProto())
                        .build())
                .collect(Collectors.toList()));
    }

    /**
     * Restarts or replaces all tasks in the specified pod.
     *
     * @param stateStore the state storage where any updated tasks will be stored
     * @param podInstance the pod whose tasks will be marked as failed
     */
    public static void killTasks(
            ConfigStore<ServiceSpec> configStore,
            Collection<Protos.TaskInfo> allTasks,
            StateStore stateStore,
            String podInstanceName,
            boolean replace) {
        Collection<Protos.TaskInfo> podTasks = TaskUtils.getPodTasks(podInstance, stateStore.fetchTasks());

        if (podTasks.isEmpty()) {
            return; // TODO notFoundResponse
        }

        if (replace) {
            Set<PodInstance> podInstances = new HashSet<>();
            for (Protos.TaskInfo taskInfo : podTasks) {
                if (taskInfo.getTaskId().getValue().isEmpty()) {
                    // Skip marking 'stub' tasks which haven't been launched as permanently failed:
                    LOGGER.info("Not marking task {} as failed due to empty taskId", taskInfo.getName());
                    continue;
                }
                try {
                    podInstances.add(TaskUtils.getPodInstance(configStore, taskInfo));
                } catch (TaskException e) {
                    LOGGER.error(String.format("Failed to get pod for task %s", taskInfo.getTaskId().getValue()), e);
                }
            }

            podInstances.forEach(podInstance -> setPermanentlyFailed(stateStore, podTasks));
        }

        for (Protos.TaskInfo taskToKill : podTasks) {
            final Protos.TaskInfo taskInfo = taskToKill.getInfo();
            if (taskToKill.hasStatus()) {
                LOGGER.info("  {} ({}): currently has status {}",
                        taskInfo.getName(),
                        taskInfo.getTaskId().getValue(),
                        taskToKill.getStatus().get().getState());
            } else {
                LOGGER.info("  {} ({}): no status available",
                        taskInfo.getName(),
                        taskInfo.getTaskId().getValue());
            }
            TaskKiller.killTask(taskInfo.getTaskId());
        }
    }
}
