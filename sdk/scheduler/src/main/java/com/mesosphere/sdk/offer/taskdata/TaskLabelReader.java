package com.mesosphere.sdk.offer.taskdata;

import com.mesosphere.sdk.offer.TaskException;
import org.apache.mesos.Protos.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides read access to task labels which are (only) read by the Scheduler.
 */
public class TaskLabelReader {

    private final LabelReader reader;

    /**
     * @see LabelReader#LabelReader(String, Labels)
     */
    public TaskLabelReader(TaskInfo taskInfo) {
        reader = new LabelReader(String.format("Task %s", taskInfo.getName()), taskInfo.getLabels());
    }

    /**
     * @see LabelReader#LabelReader(String, Labels)
     */
    public TaskLabelReader(TaskInfo.Builder taskInfoBuilder) {
        reader = new LabelReader(String.format("Task %s", taskInfoBuilder.getName()), taskInfoBuilder.getLabels());
    }

    /**
     * Returns the task type string, which was embedded in the task.
     *
     * @throws TaskException if the type could not be found.
     */
    public String getType() throws TaskException {
        return reader.getOrThrow(LabelConstants.TASK_TYPE_LABEL);
    }

    /**
     * Returns the pod instance index of the task, or throws {@link TaskException} if no index data was found.
     *
     * @throws TaskException if the index data wasn't found
     * @throws NumberFormatException if parsing the index as an integer failed
     */
    public int getIndex() throws TaskException, NumberFormatException {
        return Integer.parseInt(reader.getOrThrow(LabelConstants.TASK_INDEX_LABEL));
    }

    /**
     * Returns the string representations of any {@link Offer} {@link Attribute}s which were embedded in the task.
     */
    public List<String> getOfferAttributeStrings() {
        Optional<String> joinedAttributes = reader.getOptional(LabelConstants.OFFER_ATTRIBUTES_LABEL);
        if (!joinedAttributes.isPresent()) {
            return new ArrayList<>();
        }
        return AttributeStringUtils.toStringList(joinedAttributes.get());
    }

    /**
     * Returns the Zone in which the Task was launched.
     */
    public Optional<String> getZone() {
        return reader.getOptional(LabelConstants.OFFER_ZONE_LABEL);
    }

    /**
     * Returns the Region in which the Task was launched.
     */
    public Optional<String> getRegion() {
        return reader.getOptional(LabelConstants.OFFER_REGION_LABEL);
    }

    /**
     * Returns the hostname of the agent machine running the task.
     */
    public String getHostname() throws TaskException {
        return reader.getOrThrow(LabelConstants.OFFER_HOSTNAME_LABEL);
    }

    /**
     * Returns the ID referencing a configuration in a {@link com.mesosphere.sdk.state.ConfigStore} associated with the
     * task.
     *
     * @return the ID of the target configuration for the provided {@link TaskInfo}
     * @throws TaskException when a TaskInfo is provided which does not contain a {@link Label} with
     *                       an indicated target configuration
     */
    public UUID getTargetConfiguration() throws TaskException {
        return UUID.fromString(reader.getOrThrow(LabelConstants.TARGET_CONFIGURATION_LABEL));
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
        Optional<String> readinessCheckOptional = reader.getOptional(LabelConstants.READINESS_CHECK_LABEL);
        if (!readinessCheckOptional.isPresent() && !taskStatus.hasCheckStatus()) {
            // check not applicable: PASS
            return true;
        } else if (taskStatus.hasCheckStatus()) {
            return taskStatus.getCheckStatus().getCommand().hasExitCode() &&
                    taskStatus.getCheckStatus().getCommand().getExitCode() == 0;
        }

        // Special case: the 'readiness check passed' bit is set in TaskStatus (by the executor),
        // not in TaskInfo like other labels
        for (Label statusLabel : taskStatus.getLabels().getLabelsList()) {
            if (statusLabel.getKey().equals(LabelConstants.READINESS_CHECK_PASSED_LABEL)) {
                return statusLabel.getValue().equals(LabelConstants.BOOLEAN_LABEL_TRUE_VALUE);
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
        return Boolean.valueOf(reader.getOptional(LabelConstants.TRANSIENT_FLAG_LABEL).orElse(null));
    }

    /**
     * Returns whether the task is marked as permanently failed. This is intentionally stored in the TaskInfo as it will
     * be automatically overwritten when the TaskInfo is replaced.
     */
    public boolean isPermanentlyFailed() {
        // null is false
        return Boolean.valueOf(reader.getOptional(LabelConstants.PERMANENTLY_FAILED_LABEL).orElse(null));
    }

    /**
     * Returns whether the task has a readiness check label.
     */
    public boolean hasReadinessCheckLabel() {
        return reader.getOptional(LabelConstants.READINESS_CHECK_LABEL).isPresent();
    }
}
