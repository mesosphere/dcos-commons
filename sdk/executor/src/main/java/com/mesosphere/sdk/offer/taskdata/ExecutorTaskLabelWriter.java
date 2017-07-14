package com.mesosphere.sdk.offer.taskdata;

import org.apache.mesos.Protos.Labels;

/**
 * Provides write access to task labels which are (only) written by the Executor.
 */
public class ExecutorTaskLabelWriter {

    private final LabelWriter writer;

    public ExecutorTaskLabelWriter() {
        writer = new LabelWriter();
    }

    /**
     * Indicates to the Scheduler that an Executor's readiness check has passed.
     */
    public ExecutorTaskLabelWriter setReadinessCheckPassed() {
        writer.put(LabelConstants.READINESS_CHECK_PASSED_LABEL, LabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Returns a protobuf representation of all contained label entries.
     */
    public Labels toProto() {
        return writer.toProto();
    }
}
