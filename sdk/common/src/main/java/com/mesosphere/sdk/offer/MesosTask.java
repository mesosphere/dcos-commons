package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;

/**
 * A representation of a Mesos Task.
 **/
public class MesosTask {
    public static final String TRANSIENT_FLAG_KEY = "transient";

    private TaskInfo taskInfo;

    public MesosTask(TaskInfo taskInfo) {
        this.taskInfo = taskInfo;
    }

    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public boolean isTransient() {
        return Boolean.valueOf(getTransientValue());
    }

    private String getTransientValue() {
        if (taskInfo.hasLabels()) {
            Labels labels = taskInfo.getLabels();
            for (Label label : labels.getLabelsList()) {
                if (label.getKey().equals(TRANSIENT_FLAG_KEY)) {
                    return label.getValue();
                }
            }
        }

        return null;
    }
}
