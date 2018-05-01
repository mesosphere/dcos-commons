package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos.Labels;

/**
 * Implements common logic for write access to a task's Labels. Any access to specific values is provided (only) by
 * ExecutorLabelWriter and SchedulerLabelWriter.
 */
class LabelWriter {

    private final Map<String, String> labels;

    /**
     * Creates a new instance which is initialized with no labels.
     */
    LabelWriter() {
        this(Labels.getDefaultInstance());
    }

    /**
     * Creates a new instance which is initialized with the provided {@code labels}.
     */
    LabelWriter(Labels labels) {
        this.labels = LabelUtils.toMap(labels);
    }

    /**
     * Returns the requested label value, or an empty Optional if the value was not found.
     */
    protected Optional<String> getOptional(String key) {
        return Optional.ofNullable(labels.get(key));
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
