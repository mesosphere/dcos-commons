package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;

/**
 * Implements common logic for write access to a task's Labels. Any access to specific values is provided (only) by
 * ExecutorLabelWriter and SchedulerLabelWriter.
 */
public class LabelWriter {

    private final Map<String, String> labels;

    /**
     * Creates a new instance which is initialized with no labels.
     */
    protected LabelWriter() {
        this(Labels.getDefaultInstance());
    }

    /**
     * Creates a new instance which is initialized with the provided task's labels.
     */
    protected LabelWriter(TaskInfo taskInfo) {
        this(taskInfo.getLabels());
    }

    /**
     * Creates a new instance which is initialized with the provided task builder's labels.
     */
    protected LabelWriter(TaskInfo.Builder taskInfoBuilder) {
        this(taskInfoBuilder.getLabels());
    }

    private LabelWriter(Labels labels) {
        this.labels = LabelUtils.toMap(labels);
    }

    /**
     * Sets the provided label value, overwriting any previous value.
     */
    protected void put(String key, String val) {
        labels.put(key, val);
    }

    /**
     * Removes the provided label value, or does nothing if it was already not present.
     */
    protected void remove(String key) {
        labels.remove(key);
    }

    /**
     * Returns a Protobuf representation of all contained label entries.
     */
    public Labels toProto() {
        return LabelUtils.toProto(labels);
    }
}
