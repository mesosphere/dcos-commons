package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a default implementation of the RecoveryRequirementProvider interface.
 */
public class DefaultRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultRecoveryRequirementProvider(OfferRequirementProvider offerRequirementProvider) {
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public List<RecoveryRequirement> getTransientRecoveryRequirements(List<Protos.TaskInfo> stoppedTasks)
            throws InvalidRequirementException {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : stoppedTasks) {
            try {
                TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
                transientRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification),
                                RecoveryRequirement.RecoveryType.TRANSIENT));
            } catch (InvalidTaskSpecificationException e) {
                logger.error("Failed to generate TaskSpecification for transient recovery with exception: ", e);
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
                TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
                transientRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getNewOfferRequirement(taskSpecification),
                                RecoveryRequirement.RecoveryType.PERMANENT));
            } catch (InvalidTaskSpecificationException e) {
                logger.error("Failed to generate TaskSpecification for transient recovery with exception: ", e);
            }
        }

        return transientRecoveryRequirements;
    }
}
