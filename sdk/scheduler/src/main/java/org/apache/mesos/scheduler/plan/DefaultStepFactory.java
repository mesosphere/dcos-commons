package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the {@link StepFactory} interface.
 */
public class DefaultStepFactory implements StepFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStepFactory.class);

    private final ConfigStore configStore;
    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultStepFactory(
            ConfigStore configStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider) {
        this.configStore = configStore;
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public Step getStep(PodInstance podInstance, List<String> tasksToLaunch) throws Step.InvalidStepException, InvalidRequirementException {
        LOGGER.info("Generating step for pod: {}", podInstance.getName());

        List<Protos.TaskInfo> taskInfos = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .collect(Collectors.toList());


        try {
            if (taskInfos.isEmpty()) {
                LOGGER.info("Generating new step for: {}", podInstance.getName());
                return new DefaultStep(
                        podInstance.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                // Note: This path is for deploying new versions of tasks, unlike transient recovery
                // which is only interested in relaunching tasks as they were. So while they omit
                // placement rules in their OfferRequirement, we include them.
                Status status = getStatus(taskInfos);
                LOGGER.info("Generating existing step for: {} with status: {}", podInstance.getName(), status);
                return new DefaultStep(
                        podInstance.getName(),
                        Optional.of(offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch)),
                        status,
                        Collections.emptyList());
            }
        } catch (InvalidRequirementException e) {
            LOGGER.error("Failed to generate Step with exception: ", e);
            throw new Step.InvalidStepException(e);
        }
    }

    private Status getStatus(List<Protos.TaskInfo> taskInfos) {
        List<Status> statuses = taskInfos.stream()
                .map(taskInfo -> getStatus(taskInfo))
                .collect(Collectors.toList());

        for (Status status : statuses) {
            if (!status.equals(Status.COMPLETE)) {
                return Status.PENDING;
            }
        }

        return Status.COMPLETE;
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
