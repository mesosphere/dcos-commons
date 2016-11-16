package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.specification.TaskSpecificationProvider;
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

    private final ConfigStore configStore;
    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;
    private final TaskSpecificationProvider taskSpecificationProvider;

    public DefaultStepFactory(
            ConfigStore configStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            TaskSpecificationProvider taskSpecificationProvider) {
        this.configStore = configStore;
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.taskSpecificationProvider = taskSpecificationProvider;
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
                // Note: This path is for deploying new versions of tasks, unlike transient recovery
                // which is only interested in relaunching tasks as they were. So while they omit
                // placement rules in their OfferRequirement, we include them.
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
        UUID targetConfigId = configStore.getTargetConfig();
        UUID taskConfigId = TaskUtils.getTargetConfiguration(taskInfo);
        return targetConfigId.equals(taskConfigId);
    }
}
