package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.specification.TaskSpecificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a default implementation of the RecoveryRequirementProvider interface.
 */
public class DefaultRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryRequirementProvider.class);

    private final OfferRequirementProvider offerRequirementProvider;
    private final TaskSpecificationProvider taskSpecificationProvider;

    public DefaultRecoveryRequirementProvider(
            OfferRequirementProvider offerRequirementProvider,
            TaskSpecificationProvider taskSpecificationProvider) {
        this.offerRequirementProvider = offerRequirementProvider;
        this.taskSpecificationProvider = taskSpecificationProvider;
    }

    @Override
    public List<RecoveryRequirement> getTransientRecoveryRequirements(List<Protos.TaskInfo> stoppedTasks)
            throws InvalidRequirementException {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : stoppedTasks) {
            try {
                // Note: We intentionally remove any placement rules when performing transient recovery.
                // We aren't interested in honoring placement rules, past or future. We just want to
                // get the task back up and running where it was before.
                transientRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getExistingOfferRequirement(
                                        taskInfo, taskSpecificationProvider.getTaskSpecification(taskInfo))
                                .withoutPlacementRules(),
                                RecoveryRequirement.RecoveryType.TRANSIENT));
            } catch (TaskException e) {
                LOGGER.error("Failed to generate TaskSpecification for transient recovery with exception: ", e);
            }
        }

        return transientRecoveryRequirements;
    }

    @Override
    public List<RecoveryRequirement> getPermanentRecoveryRequirements(List<Protos.TaskInfo> failedTasks)
            throws InvalidRequirementException {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : failedTasks) {
            try {
                transientRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getNewOfferRequirement(
                                        taskSpecificationProvider.getTaskSpecification(taskInfo)),
                                RecoveryRequirement.RecoveryType.PERMANENT));
            } catch (TaskException e) {
                LOGGER.error("Failed to generate TaskSpecification for transient recovery with exception: ", e);
            }
        }

        return transientRecoveryRequirements;
    }
}
