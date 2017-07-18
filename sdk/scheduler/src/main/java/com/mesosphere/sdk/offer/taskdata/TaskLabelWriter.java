package com.mesosphere.sdk.offer.taskdata;

import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.GoalState;

/**
 * Provides write access to task labels which are (only) written by the Scheduler.
 */
public class TaskLabelWriter {

    private final LabelWriter writer;

    /**
     * @see LabelWriter#LabelWriter(Labels)
     */
    public TaskLabelWriter(TaskInfo taskInfo) {
        writer = new LabelWriter(taskInfo.getLabels());
    }

    /**
     * @see LabelWriter#LabelWriter(Labels)
     */
    public TaskLabelWriter(TaskInfo.Builder taskInfoBuilder) {
        writer = new LabelWriter(taskInfoBuilder.getLabels());
    }

    /**
     * Ensures that the task is identified as a transient task.
     */
    public TaskLabelWriter setTransient() {
        writer.put(TaskLabelConstants.TRANSIENT_FLAG_LABEL, TaskLabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Ensures that the task is not identified as a transient task.
     */
    public TaskLabelWriter clearTransient() {
        writer.remove(TaskLabelConstants.TRANSIENT_FLAG_LABEL);
        return this;
    }

    /**
     * Ensures that the task is identified as permanently failed.
     */
    public TaskLabelWriter setPermanentlyFailed() {
        writer.put(TaskLabelConstants.PERMANENTLY_FAILED_LABEL, TaskLabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Ensures that the task is not identified as permanently failed.
     */
    public TaskLabelWriter clearPermanentlyFailed() {
        writer.remove(TaskLabelConstants.PERMANENTLY_FAILED_LABEL);
        return this;
    }

    /**
     * Ensures that the task is identified as being launched for the first time at its current location.
     */
    public TaskLabelWriter setInitialLaunch() {
        writer.put(TaskLabelConstants.INITIAL_LAUNCH_LABEL, TaskLabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Ensures that the task is not identified as being launched for the first time at its current location.
     */
    public TaskLabelWriter clearInitialLaunch() {
        writer.remove(TaskLabelConstants.INITIAL_LAUNCH_LABEL);
        return this;
    }

    /**
     * Stores the provided task type string. Any existing task type is overwritten.
     */
    public TaskLabelWriter setType(String taskType) {
        writer.put(TaskLabelConstants.TASK_TYPE_LABEL, taskType);
        return this;
    }


    /**
     * Assigns the pod instance index to the provided task. Any existing index is overwritten.
     */
    public TaskLabelWriter setIndex(int index) {
        writer.put(TaskLabelConstants.TASK_INDEX_LABEL, String.valueOf(index));
        return this;
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer}.
     * Any existing stored attributes are overwritten.
     */
    public TaskLabelWriter setOfferAttributes(Offer launchOffer) {
        writer.put(TaskLabelConstants.OFFER_ATTRIBUTES_LABEL,
                AttributeStringUtils.toString(launchOffer.getAttributesList()));
        return this;
    }

    /**
     * Stores the agent hostname from the provided {@link Offer}.
     * Any existing stored hostname is overwritten.
     */
    public TaskLabelWriter setHostname(Offer launchOffer) {
        writer.put(TaskLabelConstants.OFFER_HOSTNAME_LABEL, launchOffer.getHostname());
        return this;
    }

    /**
     * Sets a label on a TaskInfo indicating the Task's {@link GoalState}, e.g. RUNNING or FINISHED.
     */
    public TaskLabelWriter setGoalState(GoalState goalState) {
        writer.put(TaskLabelConstants.GOAL_STATE_LABEL, goalState.name());
        return this;
    }

    /**
     * Sets a {@link Label} indicating the target configuration.
     *
     * @param targetConfigurationId ID referencing a particular Configuration in the {@link ConfigStore}
     */
    public TaskLabelWriter setTargetConfiguration(UUID targetConfigurationId) {
        writer.put(TaskLabelConstants.TARGET_CONFIGURATION_LABEL, targetConfigurationId.toString());
        return this;
    }

    /**
     * Stores an encoded version of the {@link HealthCheck} as a readiness check.
     * Any existing stored readiness check is overwritten.
     */
    public TaskLabelWriter setReadinessCheck(HealthCheck readinessCheck) {
        writer.put(TaskLabelConstants.READINESS_CHECK_LABEL, LabelUtils.encodeHealthCheck(readinessCheck));
        return this;
    }

    /**
     * Updates the stored readiness check, if any, to have the provided environment variable.
     * Does nothing if no readiness check is present.
     *
     * @throws TaskException if parsing a previously set {@link HealthCheck} failed
     */
    public TaskLabelWriter setReadinessCheckEnvvar(String key, String value) throws TaskException {
        Optional<HealthCheck> readinessCheck = getReadinessCheck();
        if (!readinessCheck.isPresent()) {
            return this;
        }
        HealthCheck.Builder readinessCheckBuilder = readinessCheck.get().toBuilder();
        readinessCheckBuilder.getCommandBuilder().setEnvironment(
                EnvUtils.withEnvVar(readinessCheckBuilder.getCommand().getEnvironment(), key, value));
        return setReadinessCheck(readinessCheckBuilder.build());
    }

    /**
     * Returns a protobuf representation of all contained label entries.
     */
    public Labels toProto() {
        return writer.toProto();
    }

    /**
     * Returns the embedded readiness check, or an empty Optional if no readiness check is configured.
     */
    @VisibleForTesting
    protected Optional<HealthCheck> getReadinessCheck() throws TaskException {
        Optional<String> encodedReadinessCheck = writer.getOptional(TaskLabelConstants.READINESS_CHECK_LABEL);
        return (encodedReadinessCheck.isPresent())
                ? Optional.of(LabelUtils.decodeHealthCheck(encodedReadinessCheck.get()))
                : Optional.empty();
    }
}
