package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.OtherLabelAccess;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
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

        Protos.TaskInfo taskInfo = ((LaunchOfferRecommendation) offerRecommendation).getTaskInfo();

        Protos.TaskStatus taskStatus = null;
        String taskStatusLogDetails = "";
        if (!taskInfo.getTaskId().getValue().equals("")) {
            taskStatusLogDetails += " with STAGING status";
            // Record initial TaskStatus of STAGING:
            Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING);

            if (taskInfo.hasExecutor()) {
                taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
            }

            taskStatus = taskStatusBuilder.build();
        }

        // If this task is brand new (hasn't been launched on this agent before), we include a 'first_launch' label.
        // This allows us to give up on this agent if it's no good.
        if (isFirstLaunchOnAgent(stateStore.fetchTask(taskInfo.getName()))) {
            taskStatusLogDetails += " and initial launch flag";
            taskInfo = taskInfo.toBuilder()
                    .setLabels(new TaskLabelWriter(taskInfo).setInitialLaunch().toProto())
                    .build();
        }

        logger.info("Persisting launch operation{}: {}", taskStatusLogDetails, TextFormat.shortDebugString(taskInfo));

        updateResourcesWithinResourceSet(taskInfo);
        stateStore.storeTasks(Collections.singletonList(taskInfo));
        if (taskStatus != null) {
            stateStore.storeStatus(taskStatus);
        }
    }

    private static boolean isFirstLaunchOnAgent(Optional<TaskInfo> priorTaskInfo) {
        if (!priorTaskInfo.isPresent()) {
            // This task has never been launched anywhere
            return true;
        }

        if (new TaskLabelReader(priorTaskInfo.get()).isPermanentlyFailed()) {
            // This task had been launched somewhere before, but now it's being launched again in a new location
            return true;
        }

        return false;
    }

    /**
     * This method keeps the resources associated with tasks in the state store up to date, when a task which shares
     * their resource-set is launched.
     * @param taskInfo the task being launched
     * @throws TaskException is thrown on a failure to read meta-data from the TaskInfo
     */
    @VisibleForTesting
    void updateResourcesWithinResourceSet(Protos.TaskInfo taskInfo) throws TaskException {
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
        stateStore.storeTasks(updateTasksWithResources(taskInfosWithSameResourceSet, taskInfo.getResourcesList()));
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
        logger.info("Updating resources for tasks: {}", taskNamesToUpdate);

        // Fetch any existing matching TaskInfos from the state store
        List<Protos.TaskInfo> taskInfosToUpdate = taskNamesToUpdate.stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .collect(Collectors.toList());
        List<String> taskIds = taskInfosToUpdate.stream()
                .map(taskInfoToUpdate -> taskInfoToUpdate.getTaskId().getValue())
                .collect(Collectors.toList());
        logger.info("Updating resources in TaskInfos: {}", taskIds);
        return taskInfosToUpdate;
    }

    /**
     * Returns {@link Protos.TaskInfo}s which have been updated to contain the provided resource list, overwriting any
     * previous resource information.
     */
    private static Collection<Protos.TaskInfo> updateTasksWithResources(
            Collection<Protos.TaskInfo> taskInfosToUpdate, Collection<Protos.Resource> resources) {
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        for (Protos.TaskInfo taskInfoToUpdate : taskInfosToUpdate) {
            updatedTaskInfos.add(
                    Protos.TaskInfo.newBuilder(taskInfoToUpdate)
                            .clearResources()
                            .addAllResources(resources)
                            .build());
        }
        return updatedTaskInfos;
    }
}
