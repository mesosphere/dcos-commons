package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
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

        Protos.TaskStatus taskStatus = null;
        if (!taskInfo.getTaskId().getValue().equals("")) {
            // Record initial TaskStatus of STAGING:
            Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING);

            if (taskInfo.hasExecutor()) {
                taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
            }

            taskStatus = taskStatusBuilder.build();
        }

        logger.info("Persisting launch operation{}: {}",
                taskStatus != null ? " with STAGING status" : "",
                TextFormat.shortDebugString(taskInfo));

        updateTaskResourcesWithinResourceSet(taskInfo);
        stateStore.storeTasks(Collections.singletonList(taskInfo));
        if (taskStatus != null) {
            stateStore.storeStatus(taskStatus);
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

        int index = new TaskLabelReader(taskInfo).getIndex();
        PodInstance podInstance = new DefaultPodInstance(podSpecOptional.get(), index);

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
