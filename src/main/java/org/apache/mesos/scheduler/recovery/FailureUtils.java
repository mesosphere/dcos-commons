package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.ResourceUtils;

/**
 * Created by gabriel on 8/20/16.
 */
public class FailureUtils {
    private static final String PERMANENTLY_FAILED_KEY = "permanently-failed";

    public static boolean labeledAsFailed(Protos.TaskInfo taskInfo) {
        for (Protos.Label label : taskInfo.getLabels().getLabelsList()) {
            if (label.getKey().equals(PERMANENTLY_FAILED_KEY) && Boolean.valueOf(label.getValue())) {
                return true;
            }
        }

        return false;
    }

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
