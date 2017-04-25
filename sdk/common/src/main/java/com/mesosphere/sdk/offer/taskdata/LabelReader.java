package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.TaskException;

/**
 * Implements common logic for read access to a task's Labels. Any access to component-specific values is provided by
 * {@link com.mesosphere.sdk.offer.taskdata.ExecutorLabelReader} and
 * {@link com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader}.
 */
public class LabelReader {

    private final String taskName;
    private final Map<String, String> labels;

    /**
     * Creates a new instance for reading the provided task's labels.
     */
    protected LabelReader(TaskInfo taskInfo) {
        this(taskInfo.getName(), taskInfo.getLabels());
    }

    /**
     * Creates a new instance for reading the provided task builder's labels.
     */
    protected LabelReader(TaskInfo.Builder taskInfoBuilder) {
        this(taskInfoBuilder.getName(), taskInfoBuilder.getLabels());
    }

    /**
     * Creates a new instance.
     *
     * @param taskName the name of the task, only used in error messages if there are problems with the label content
     * @param labels the labels to be read from
     */
    private LabelReader(String taskName, Labels labels) {
        this.taskName = taskName;
        this.labels = LabelUtils.toMap(labels);
    }

    /**
     * Returns the requested label value, or throws an exception if the value was not found.
     */
    protected String getOrThrow(String key) throws TaskException {
        String value = labels.get(key);
        if (value == null) {
            throw new TaskException(String.format(
                    "Task %s is missing label %s. Current labels are: %s", taskName, key, labels));
        }
        return value;
    }

    /**
     * Returns the requested label value, or an empty Optional if the value was not found.
     */
    protected Optional<String> getOptional(String key) {
        return Optional.ofNullable(labels.get(key));
    }
}
