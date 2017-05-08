package com.mesosphere.sdk.scheduler.recovery;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureUtils.class);
    private static final String PERMANENTLY_FAILED_KEY = "permanently-failed";

    /**
     * Check if a Task has been marked as permanently failed.
     * @param taskInfo The Task to check for failure.
     * @return True if the Task has been marked, false otherwise.
     */
    public static boolean isLabeledAsFailed(Protos.TaskInfo taskInfo) {
        for (Protos.Label label : taskInfo.getLabels().getLabelsList()) {
            if (label.getKey().equals(PERMANENTLY_FAILED_KEY) && Boolean.valueOf(label.getValue())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Mark a task as permanently failed.  This new marked Task should be persistently stored.
     * @param taskInfo The Task to be marked.
     * @return The marked TaskInfo which may be a copy of the original TaskInfo.
     */
    static Protos.TaskInfo markFailed(Protos.TaskInfo taskInfo) {
        if (isLabeledAsFailed(taskInfo)) {
            return taskInfo;
        }
        return Protos.TaskInfo.newBuilder(taskInfo)
                .setLabels(Protos.Labels.newBuilder(taskInfo.getLabels())
                        .addLabels(Protos.Label.newBuilder()
                                .setKey(PERMANENTLY_FAILED_KEY)
                                .setValue(String.valueOf(true))))
                .build();
    }

    public static void markFailed(PodInstance podInstance, StateStore stateStore) {
        stateStore.storeTasks(
                getTaskInfos(podInstance, stateStore).stream()
                        .map(taskInfo -> FailureUtils.markFailed(taskInfo))
                        .collect(Collectors.toList()));
    }

    /**
     * Remove the permanently failed label from the TaskInfo.
     */
    static Protos.TaskInfo clearFailed(Protos.TaskInfo taskInfo) {
        if (!isLabeledAsFailed(taskInfo)) {
            return taskInfo;
        }

        LOGGER.info("Clearing permanent failure mark from: {}", TextFormat.shortDebugString(taskInfo));
        List<Protos.Label> labels = taskInfo.getLabels().getLabelsList().stream()
                .filter(label -> label.hasKey() && !label.getKey().equals(PERMANENTLY_FAILED_KEY))
                .collect(Collectors.toList());

        return Protos.TaskInfo.newBuilder(taskInfo)
                .setLabels(Protos.Labels.newBuilder().addAllLabels(labels))
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
