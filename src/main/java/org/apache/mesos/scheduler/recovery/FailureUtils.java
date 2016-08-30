package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.ResourceUtils;

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
    public static boolean labeledAsFailed(Protos.TaskInfo taskInfo) {
        for (Protos.Label label : taskInfo.getLabels().getLabelsList()) {
            if (label.getKey().equals(PERMANENTLY_FAILED_KEY) && Boolean.valueOf(label.getValue())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Mark a task as permanently failed.  This new marekd Task should be persistently stored.
     * @param taskInfo The Task to be marked.
     * @return The marked Task.
     */
    public static Protos.TaskInfo markFailed(Protos.TaskInfo taskInfo) {
        if (!labeledAsFailed(taskInfo)) {
            taskInfo = ResourceUtils.clearResourceIds(taskInfo);
            return Protos.TaskInfo.newBuilder(taskInfo)
                    .setLabels(Protos.Labels.newBuilder(taskInfo.getLabels())
                            .addLabels(Protos.Label.newBuilder()
                                    .setKey(PERMANENTLY_FAILED_KEY)
                                    .setValue(String.valueOf(true))))
                    .build();
        } else {
            return  taskInfo;
        }
    }
}
