package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.TaskSpec;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    public Step getStep(PodInstance podInstance) throws Step.InvalidStepException {
        LOGGER.info("Generating step for pod: {}", podInstance.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(podInstance.getName());

        try {
            if (!taskInfoOptional.isPresent()) {
                LOGGER.info("Generating new step for: {}", podInstance.getName());
                return new DefaultStep(
                        podInstance.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(podInstance)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                // Note: This path is for deploying new versions of tasks, unlike transient recovery
                // which is only interested in relaunching tasks as they were. So while they omit
                // placement rules in their OfferRequirement, we include them.
                Status status = getStatus(taskInfoOptional.get());
                LOGGER.info("Generating existing step for: {} with status: {}", podInstance.getName(), status);
                return new DefaultStep(
                        taskInfoOptional.get().getName(),
                        Optional.of(
                                offerRequirementProvider.getExistingOfferRequirement(
                                        getTaskInfosShouldBeRunning(podInstance),
                                        getExecutor(podInstance),
                                        podInstance)),
                        status,
                        Collections.emptyList());
            }
        } catch (InvalidRequirementException e) {
            LOGGER.error("Failed to generate Step with exception: ", e);
            throw new Step.InvalidStepException(e);
        }
    }

    private List<Protos.TaskInfo> getTaskInfosShouldBeRunning(PodInstance podInstance) {
        List<Protos.TaskInfo> podTasks = getPodTasks(podInstance);

        List<Protos.TaskInfo> tasksShouldBeRunning = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : podTasks) {
            Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(taskInfo, podInstance.getPod());

            if (taskSpecOptional.isPresent() && taskSpecOptional.get().getGoal().equals(TaskSpec.GoalState.RUNNING)) {
                tasksShouldBeRunning.add(taskInfo);
            }
        }

        return tasksShouldBeRunning;
    }

    private Optional<Protos.ExecutorInfo> getExecutor(PodInstance podInstance) {
        List<Protos.TaskInfo> shouldBeRunningTasks = getTaskInfosShouldBeRunning(podInstance);

        for (Protos.TaskInfo taskInfo : shouldBeRunningTasks) {
            Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());
            if (taskStatusOptional.isPresent() && taskStatusOptional.get().getState().equals(Protos.TaskState.TASK_RUNNING)) {
                LOGGER.info("Reusing executor: ", taskInfo.getExecutor());
                return Optional.of(taskInfo.getExecutor());
            }
        }

        LOGGER.info("No running executor found.");
        return Optional.empty();
    }

    private List<Protos.TaskInfo> getPodTasks(PodInstance podInstance) {
        return stateStore.fetchTasks().stream()
                .filter(taskInfo -> {
                    try {
                        return TaskUtils.getTaskType(taskInfo).equals(podInstance.getName());
                    } catch (TaskException e) {
                        LOGGER.error("Encountered ");
                        return false;
                    }
                })
                .collect(Collectors.toList());
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
