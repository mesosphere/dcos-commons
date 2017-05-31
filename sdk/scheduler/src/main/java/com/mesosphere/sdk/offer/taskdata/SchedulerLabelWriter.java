package com.mesosphere.sdk.offer.taskdata;

import java.util.UUID;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.specification.GoalState;

/**
 * Provides write access to task labels which are (only) written by the Scheduler.
 */
public class SchedulerLabelWriter extends LabelWriter {

    /**
     * @see LabelWriter#LabelWriter(TaskInfo)
     */
    public SchedulerLabelWriter(TaskInfo taskInfo) {
        super(taskInfo);
    }

    /**
     * @see LabelWriter#LabelWriter(org.apache.mesos.Protos.TaskInfo.Builder)
     */
    public SchedulerLabelWriter(TaskInfo.Builder taskInfoBuilder) {
        super(taskInfoBuilder);
    }

    /**
     * Ensures that the task is identified as a transient task.
     */
    public SchedulerLabelWriter setTransient() {
        put(LabelConstants.TRANSIENT_FLAG_LABEL, "true");
        return this;
    }

    /**
     * Ensures that the task is not identified as a transient task.
     */
    public SchedulerLabelWriter clearTransient() {
        remove(LabelConstants.TRANSIENT_FLAG_LABEL);
        return this;
    }

    /**
     * Stores the provided task type string. Any existing task type is overwritten.
     */
    public SchedulerLabelWriter setType(String taskType) {
        put(LabelConstants.TASK_TYPE_LABEL, taskType);
        return this;
    }


    /**
     * Assigns the pod instance index to the provided task. Any existing index is overwritten.
     */
    public SchedulerLabelWriter setIndex(int index) {
        put(LabelConstants.TASK_INDEX_LABEL, String.valueOf(index));
        return this;
    }

    /**
     * Stores the {@link Attribute}s from the provided {@link Offer}.
     * Any existing stored attributes are overwritten.
     */
    public SchedulerLabelWriter setOfferAttributes(Offer launchOffer) {
        put(LabelConstants.OFFER_ATTRIBUTES_LABEL, AttributeStringUtils.toString(launchOffer.getAttributesList()));
        return this;
    }

    /**
     * Stores the agent hostname from the provided {@link Offer}.
     * Any existing stored hostname is overwritten.
     */
    public SchedulerLabelWriter setHostname(Offer launchOffer) {
        put(LabelConstants.OFFER_HOSTNAME_LABEL, launchOffer.getHostname());
        return this;
    }

    /**
     * Sets a label on a TaskInfo indicating the Task's {@link GoalState}, e.g. RUNNING or FINISHED.
     */
    public SchedulerLabelWriter setGoalState(GoalState goalState) {
        put(LabelConstants.GOAL_STATE_LABEL, goalState.name());
        return this;
    }

    /**
     * Sets a {@link Label} indicating the target configuration.
     *
     * @param targetConfigurationId ID referencing a particular Configuration in the {@link ConfigStore}
     */
    public SchedulerLabelWriter setTargetConfiguration(UUID targetConfigurationId) {
        put(LabelConstants.TARGET_CONFIGURATION_LABEL, targetConfigurationId.toString());
        return this;
    }

    /**
     * Stores an encoded version of the {@link HealthCheck} as a readiness check.
     * Any existing stored readiness check is overwritten.
     */
    public SchedulerLabelWriter setReadinessCheck(HealthCheck readinessCheck) {
        put(LabelConstants.READINESS_CHECK_LABEL, LabelUtils.encodeHealthCheck(readinessCheck));
        return this;
    }
}
