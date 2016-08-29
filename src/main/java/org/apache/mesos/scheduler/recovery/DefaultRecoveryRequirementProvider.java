package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 8/29/16.
 */
public class DefaultRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultRecoveryRequirementProvider(OfferRequirementProvider offerRequirementProvider) {
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public List<RecoveryRequirement> getTransientRecoveryOfferRequirements(List<Protos.TaskInfo> stoppedTasks)
            throws InvalidRequirementException {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : stoppedTasks) {
            TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
            transientRecoveryRequirements.add(
                    new DefaultRecoveryRequirement(
                            offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification),
                            RecoveryRequirement.RecoveryType.TRANSIENT));
        }

        return transientRecoveryRequirements;
    }

    @Override
    public List<RecoveryRequirement> getPermanentRecoveryOfferRequirements(List<Protos.TaskInfo> failedTasks)
            throws InvalidRequirementException {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : failedTasks) {
            TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
            transientRecoveryRequirements.add(
                    new DefaultRecoveryRequirement(
                            offerRequirementProvider.getNewOfferRequirement(taskSpecification),
                            RecoveryRequirement.RecoveryType.PERMANENT));
        }

        return transientRecoveryRequirements;
    }
}
