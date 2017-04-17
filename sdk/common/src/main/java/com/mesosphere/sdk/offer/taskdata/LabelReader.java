package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.offer.TaskException;

/**
 * Implements common logic for read access to a task's Labels. Any access to component-specific values is provided by
 * ExecutorLabelReader and SchedulerLabelReader.
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
     * Returns the readiness check to be run by the Executor on task startup.
     */
    public Optional<HealthCheck> getReadinessCheck() throws TaskException {
        Optional<String> readinessCheckStrOptional = getOptional(LabelConstants.READINESS_CHECK_LABEL);
        if (!readinessCheckStrOptional.isPresent()) {
            return Optional.empty();
        }

        byte[] decodedBytes = Base64.decodeBase64(readinessCheckStrOptional.get());
        try {
            return Optional.of(HealthCheck.parseFrom(decodedBytes));
        } catch (InvalidProtocolBufferException e) {
            throw new TaskException(e);
        }
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
