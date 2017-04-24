package com.mesosphere.sdk.offer.taskdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;

import com.mesosphere.sdk.offer.TaskException;

/**
 * Provides read access to task labels which are (only) read by the Scheduler.
 */
public class SchedulerLabelReader extends LabelReader {

    /**
     * @see LabelReader#LabelReader(TaskInfo)
     */
    public SchedulerLabelReader(TaskInfo taskInfo) {
        super(taskInfo);
    }

    /**
     * @see LabelReader#LabelReader(org.apache.mesos.Protos.TaskInfo.Builder)
     */
    public SchedulerLabelReader(TaskInfo.Builder taskInfoBuilder) {
        super(taskInfoBuilder);
    }

    /**
     * Returns the task type string, which was embedded in the task.
     *
     * @throws TaskException if the type could not be found.
     */
    public String getType() throws TaskException {
        return getOrThrow(LabelConstants.TASK_TYPE_LABEL);
    }

    /**
     * Returns the pod instance index of the task, or throws {@link TaskException} if no index data was found.
     *
     * @throws TaskException if the index data wasn't found
     * @throws NumberFormatException if parsing the index as an integer failed
     */
    public int getIndex() throws TaskException {
        return Integer.parseInt(getOrThrow(LabelConstants.TASK_INDEX_LABEL));
    }

    /**
     * Returns the string representations of any {@link Offer} {@link Attribute}s which were embedded in the task.
     */
    public List<String> getOfferAttributeStrings() {
        Optional<String> joinedAttributes = getOptional(LabelConstants.OFFER_ATTRIBUTES_LABEL);
        if (!joinedAttributes.isPresent()) {
            return new ArrayList<>();
        }
        return AttributeStringUtils.toStringList(joinedAttributes.get());
    }

    /**
     * Returns the hostname of the agent machine running the task.
     */
    public String getHostname() throws TaskException {
        return getOrThrow(LabelConstants.OFFER_HOSTNAME_LABEL);
    }

    /**
     * Returns the ID referencing a configuration in a {@link ConfigStore} associated with the task.
     *
     * @return the ID of the target configuration for the provided {@link TaskInfo}
     * @throws TaskException when a TaskInfo is provided which does not contain a {@link Label} with
     *                       an indicated target configuration
     */
    public UUID getTargetConfiguration() throws TaskException {
        return UUID.fromString(getOrThrow(LabelConstants.TARGET_CONFIGURATION_LABEL));
    }

    /**
     * Returns whether or not a readiness check succeeded.  If the indicated TaskInfo does not have
     * a readiness check, then this method indicates that the readiness check has passed.  Otherwise
     * failures to parse readiness checks are interpreted as readiness check failures.  If some value other
     * than "true" is present in the readiness check label of the TaskStatus, the readiness check has
     * failed.
     *
     * @param taskStatus A TaskStatus which may or may not contain a readiness check outcome label
     * @return the result of a readiness check for the indicated TaskStatus
     */
    public boolean isReadinessCheckSucceeded(TaskStatus taskStatus) {
        Optional<String> readinessCheckOptional = getOptional(LabelConstants.READINESS_CHECK_LABEL);
        if (!readinessCheckOptional.isPresent()) {
            // check not applicable: PASS
            return true;
        }

        // Special case: the 'readiness check passed' bit is set in TaskStatus (by the executor),
        // not in TaskInfo like other labels
        for (Label statusLabel : taskStatus.getLabels().getLabelsList()) {
            if (statusLabel.getKey().equals(LabelConstants.READINESS_CHECK_PASSED_LABEL)) {
                return statusLabel.getValue().equals(LabelConstants.READINESS_CHECK_PASSED_LABEL_VALUE);
            }
        }
        return false;
    }

    /**
     * Returns whether the task is marked as transient. This identifies a 'task' which isn't actually launched, but is
     * instead created to fill reserved resources.
     */
    public boolean isTransient() {
        // null is false
        return Boolean.valueOf(getOptional(LabelConstants.TRANSIENT_FLAG_LABEL).orElse(null));
    }
}
