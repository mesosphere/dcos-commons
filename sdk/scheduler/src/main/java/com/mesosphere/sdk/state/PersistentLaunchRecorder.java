package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Records the result of launched tasks to persistent storage.
 */
public class PersistentLaunchRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final ServiceSpec serviceSpec;

    public PersistentLaunchRecorder(StateStore stateStore, ServiceSpec serviceSpec) {
        this.stateStore = stateStore;
        this.serviceSpec = serviceSpec;
    }

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        if (!(offerRecommendation instanceof LaunchOfferRecommendation)) {
            return;
        }

        LaunchOfferRecommendation launchOfferRecommendation = (LaunchOfferRecommendation) offerRecommendation;
        Protos.TaskInfo taskInfo = launchOfferRecommendation.getStoreableTaskInfo();

        final String launchDetails;
        FailureUtils.LaunchType launchType = FailureUtils.getLaunchType(stateStore.fetchTask(taskInfo.getName()));
        switch (launchType) {
        case INITIAL_LAUNCH:
            launchDetails = "initial launch";
            break;
        case RELAUNCH:
            launchDetails = "relaunch";
            break;
        default:
            launchDetails = ""; // happy compiler
            throw new IllegalStateException("Unsupported launch type: " + launchType);
        }

        Optional<Protos.TaskStatus> taskStatus = Optional.empty();
        String taskStatusDetails = "";
        if (!taskInfo.getTaskId().getValue().equals("")) {
            // Initialize the task status as TASK_STAGING. In practice we should never actually receive a TASK_STAGING
            // status from Mesos so this is effectively an internal stub for the scheduler's own use.
            taskStatusDetails = " with STAGING status";

            Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING);
            if (taskInfo.hasExecutor()) {
                taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
            }

            if (launchType == FailureUtils.LaunchType.INITIAL_LAUNCH) {
                AuxLabelAccess.setInitialLaunch(taskStatusBuilder);
            }

            taskStatus = Optional.of(taskStatusBuilder.build());
        }

        logger.info("Persisting {} operation{} for {}: {}",
                launchDetails, taskStatusDetails, taskInfo.getName(), TextFormat.shortDebugString(taskInfo));

        updateTaskResourcesWithinResourceSet(taskInfo);
        stateStore.storeTasks(Collections.singletonList(taskInfo));
        if (taskStatus.isPresent()) {
            stateStore.storeStatus(taskStatus.get());
        }
    }

    /**
     * This method keeps the resources associated with tasks in the state store up to date, when a task which shares
     * their resource-set is launched.
     * @param taskInfo the task being launched
     * @throws TaskException is thrown on a failure to read meta-data from the TaskInfo
     */
    @VisibleForTesting
    void updateTaskResourcesWithinResourceSet(Protos.TaskInfo taskInfo) throws TaskException {
        // Find the PodSpec + TaskSpec for this TaskInfo
        Optional<PodSpec> podSpecOptional = TaskUtils.getPodSpec(serviceSpec, taskInfo);
        if (!podSpecOptional.isPresent()) {
            return;
        }

        PodInstance podInstance =
                new DefaultPodInstance(podSpecOptional.get(), new TaskLabelReader(taskInfo).getIndex());

        Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(podInstance, taskInfo.getName());
        if (!taskSpecOptional.isPresent()) {
            return;
        }

        // Update any other TaskInfos in this resource set to have the same resources:
        Collection<Protos.TaskInfo> taskInfosWithSameResourceSet =
                getOtherTasksInResourceSet(podInstance, taskSpecOptional.get());
        stateStore.storeTasks(updateTasksWithResources(
                taskInfosWithSameResourceSet,
                taskInfo.getResourcesList(),
                taskInfo.hasExecutor() ? Optional.of(taskInfo.getExecutor().getResourcesList()) : Optional.empty()));
    }

    /**
     * Returns a list of other tasks whose pod and resource set match the provided task information.
     * The returned list will avoid including the same task that was provided.
     */
    private Collection<Protos.TaskInfo> getOtherTasksInResourceSet(PodInstance podInstance, TaskSpec sourceTaskSpec) {
        // Find the names of tasks sharing the resource set being used in this launch
        List<String> taskNamesToUpdate = podInstance.getPod().getTasks().stream()
                // Avoid returning sourceTask itself:
                .filter(taskSpec -> !taskSpec.getName().equals(sourceTaskSpec.getName()))
                .filter(taskSpec -> taskSpec.getResourceSet().equals(sourceTaskSpec.getResourceSet()))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        // Fetch any existing matching TaskInfos from the state store
        List<Protos.TaskInfo> taskInfosToUpdate = taskNamesToUpdate.stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .collect(Collectors.toList());

        List<String> taskIds = taskInfosToUpdate.stream()
                .map(taskInfoToUpdate -> taskInfoToUpdate.getTaskId().getValue())
                .collect(Collectors.toList());
        logger.info("Updating resources for other tasks sharing resource set '{}': names={} => ids={}",
                sourceTaskSpec.getResourceSet().getId(), taskNamesToUpdate, taskIds);

        return taskInfosToUpdate;
    }

    /**
     * Returns {@link Protos.TaskInfo}s which have been updated to contain the provided resource list, overwriting any
     * previous resource information.
     */
    private static Collection<Protos.TaskInfo> updateTasksWithResources(
            Collection<Protos.TaskInfo> taskInfosToUpdate,
            Collection<Protos.Resource> taskResources,
            Optional<Collection<Protos.Resource>> executorResources) {
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        for (Protos.TaskInfo taskInfoToUpdate : taskInfosToUpdate) {
            Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(taskInfoToUpdate);
            taskBuilder
                    .clearResources()
                    .addAllResources(taskResources);
            if (executorResources.isPresent()) {
                taskBuilder.getExecutorBuilder()
                        .clearResources()
                        .addAllResources(executorResources.get());
            }
            updatedTaskInfos.add(taskBuilder.build());
        }
        return updatedTaskInfos;
    }
}
