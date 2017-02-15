package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.ResourceUtils;
import org.apache.mesos.Protos;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the handling of failed Tasks.
 */
public class FailureUtils {
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
    public static Protos.TaskInfo markFailed(Protos.TaskInfo taskInfo) {
        if (isLabeledAsFailed(taskInfo)) {
            return taskInfo;
        }
        taskInfo = ResourceUtils.clearResourceIds(taskInfo);
        taskInfo = ResourceUtils.clearPersistence(taskInfo);
        return Protos.TaskInfo.newBuilder(taskInfo)
                .setLabels(Protos.Labels.newBuilder(taskInfo.getLabels())
                        .addLabels(Protos.Label.newBuilder()
                                .setKey(PERMANENTLY_FAILED_KEY)
                                .setValue(String.valueOf(true))))
                .build();
    }

    /**
     * Remove the permanently failed label from the TaskInfo.
     */
    public static Protos.TaskInfo clearFailed(Protos.TaskInfo taskInfo) {
        if (!isLabeledAsFailed(taskInfo)) {
            return taskInfo;
        }

        List<Protos.Label> labels = taskInfo.getLabels().getLabelsList().stream()
                .filter(label -> label.hasKey() && !label.getKey().equals(PERMANENTLY_FAILED_KEY))
                .collect(Collectors.toList());

        return Protos.TaskInfo.newBuilder(taskInfo)
                .setLabels(Protos.Labels.newBuilder().addAllLabels(labels))
                .build();
    }
}
