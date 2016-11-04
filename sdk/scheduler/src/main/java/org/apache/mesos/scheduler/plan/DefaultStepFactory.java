package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigTargetStore;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * This class is a default implementation of the {@link StepFactory} interface.
 */
public class DefaultStepFactory implements StepFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStepFactory.class);

    private final ConfigTargetStore configTargetStore;
    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultStepFactory(
            ConfigTargetStore configTargetStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider) {
        this.configTargetStore = configTargetStore;
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public Step getStep(TaskSpecification taskSpecification) throws Step.InvalidStepException {
        LOGGER.info("Generating step for: {}", taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());

        try {
            if (!taskInfoOptional.isPresent()) {
                LOGGER.info("Generating new step for: {}", taskSpecification.getName());
                return new DefaultStep(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(taskSpecification)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                Status status = getStatus(taskInfoOptional.get());
                LOGGER.info("Generating existing step for: {} with status: {}", taskSpecification.getName(), status);
                return new DefaultStep(
                        taskInfoOptional.get().getName(),
                        Optional.of(offerRequirementProvider.getExistingOfferRequirement(
                                taskInfoOptional.get(), taskSpecification)),
                        status,
                        Collections.emptyList());
            }
        } catch (InvalidRequirementException e) {
            LOGGER.error("Failed to generate Step with exception: ", e);
            throw new Step.InvalidStepException(e);
        }
    }

    private Status getStatus(Protos.TaskInfo taskInfo) {
        try {
            if (isOnTarget(taskInfo)) {
                return Status.COMPLETE;
            }
        } catch (ConfigStoreException | TaskException e) {
            LOGGER.error("Failed to determine initial Step status so defaulting to PENDING.", e);
        }

        return Status.PENDING;
    }

    private boolean isOnTarget(Protos.TaskInfo taskInfo) throws ConfigStoreException, TaskException {
        UUID targetConfigId = configTargetStore.getTargetConfig();
        UUID taskConfigId = TaskUtils.getTargetConfiguration(taskInfo);
        return targetConfigId.equals(taskConfigId);
    }
}
