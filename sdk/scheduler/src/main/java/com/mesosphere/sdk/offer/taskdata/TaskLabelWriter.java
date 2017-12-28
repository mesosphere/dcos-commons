package com.mesosphere.sdk.offer.taskdata;

import java.util.Optional;
import java.util.UUID;

import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.TaskException;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

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
     * Ensures that the task is identified as a transient task. This is a "task" which is never launched, only passed to
     * Mesos as a way to capture some resources. This is used for e.g. reserving space to run sidecar tasks.
     */
    public TaskLabelWriter setTransient() {
        writer.put(LabelConstants.TRANSIENT_FLAG_LABEL, LabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Ensures that the task is identified as permanently failed. This is intentionally stored in the TaskInfo as it
     * will be automatically overwritten when the TaskInfo is replaced. As such it will automatically be "cleared" when
     * the TaskInfo is regenerated in the offer cycle.
     */
    public TaskLabelWriter setPermanentlyFailed() {
        writer.put(LabelConstants.PERMANENTLY_FAILED_LABEL, LabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
        return this;
    }

    /**
     * Stores the provided task type string. Any existing task type is overwritten.
     */
    public TaskLabelWriter setType(String taskType) {
        writer.put(LabelConstants.TASK_TYPE_LABEL, taskType);
        return this;
    }


    /**
     * Assigns the pod instance index to the provided task. Any existing index is overwritten.
     */
    public TaskLabelWriter setIndex(int index) {
        writer.put(LabelConstants.TASK_INDEX_LABEL, String.valueOf(index));
        return this;
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer}.
     * Any existing stored attributes are overwritten.
     */
    public TaskLabelWriter setOfferAttributes(Offer launchOffer) {
        writer.put(LabelConstants.OFFER_ATTRIBUTES_LABEL,
                AttributeStringUtils.toString(launchOffer.getAttributesList()));
        return this;
    }

    /**
     * Stores the {@link org.apache.mesos.Protos.DomainInfo.FaultDomain.ZoneInfo}.
     * Any existing stored zone information is overwritten.
     */
    public TaskLabelWriter setZone(Protos.DomainInfo.FaultDomain.ZoneInfo zone) {
        writer.put(LabelConstants.OFFER_ZONE_LABEL, zone.getName());
        return this;
    }

    /**
     * Stores the {@link org.apache.mesos.Protos.DomainInfo.FaultDomain.RegionInfo}.
     * Any existing stored region information is overwritten.
     */
    public TaskLabelWriter setRegion(Protos.DomainInfo.FaultDomain.RegionInfo region) {
        writer.put(LabelConstants.OFFER_REGION_LABEL, region.getName());
        return this;
    }

    /**
     * Stores the agent hostname from the provided {@link Offer}.
     * Any existing stored hostname is overwritten.
     */
    public TaskLabelWriter setHostname(Offer launchOffer) {
        writer.put(LabelConstants.OFFER_HOSTNAME_LABEL, launchOffer.getHostname());
        return this;
    }

    /**
     * Sets a label on a TaskInfo indicating the Task's {@link GoalState}, e.g. RUNNING, FINISH or ONCE.
     */
    public TaskLabelWriter setGoalState(GoalState goalState) {
        writer.put(LabelConstants.GOAL_STATE_LABEL, goalState.name());
        return this;
    }

    /**
     * Sets a {@link Label} indicating the target configuration.
     *
     * @param targetConfigurationId ID referencing a particular Configuration in the
     *                              {@link com.mesosphere.sdk.state.ConfigStore}
     */
    public TaskLabelWriter setTargetConfiguration(UUID targetConfigurationId) {
        writer.put(LabelConstants.TARGET_CONFIGURATION_LABEL, targetConfigurationId.toString());
        return this;
    }

    /**
     * Stores an encoded version of the {@link HealthCheck} as a readiness check.
     * Any existing stored readiness check is overwritten.
     */
    public TaskLabelWriter setReadinessCheck(HealthCheck readinessCheck) {
        writer.put(
                LabelConstants.READINESS_CHECK_LABEL,
                LabelUtils.encodeHealthCheck(readinessCheck.toBuilder().setConsecutiveFailures(0).build()));
        return this;
    }

    /**
     * Updates the stored readiness check, if any, to have the provided environment variable.
     * Does nothing if no readiness check is present.
     *
     * @throws TaskException if parsing a previously set {@link HealthCheck}
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
        Optional<String> encodedReadinessCheck = writer.getOptional(LabelConstants.READINESS_CHECK_LABEL);
        return (encodedReadinessCheck.isPresent())
                ? Optional.of(LabelUtils.decodeHealthCheck(encodedReadinessCheck.get()))
                : Optional.empty();
    }
}
