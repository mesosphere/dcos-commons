package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos.Labels;

import com.mesosphere.sdk.offer.TaskException;

/**
 * Implements common logic for read access to a task's Labels. Any access to component-specific values is provided by
 * subclasses.
 */
class LabelReader {

    private final String name;
    private final Map<String, String> labels;

    /**
     * Creates a new instance.
     *
     * @param taskName the name of the task, only used in error messages if there are problems with the label content
     * @param labels the labels to be read from
     */
    LabelReader(String taskName, Labels labels) {
        this.name = taskName;
        this.labels = LabelUtils.toMap(labels);
    }

    /**
     * Returns the requested label value, or throws an exception if the value was not found.
     */
    protected String getOrThrow(String key) throws TaskException {
        String value = labels.get(key);
        if (value == null) {
            throw new TaskException(String.format(
                    "%s is missing label %s. Current labels are: %s", name, key, labels));
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
