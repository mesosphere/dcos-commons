package com.mesosphere.sdk.state;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

        updateResources(taskInfo);
        stateStore.storeTasks(Arrays.asList(taskInfo));
        if (taskStatus != null) {
            stateStore.storeStatus(taskStatus);
        }
    }

    public void updateResources(Protos.TaskInfo taskInfo) throws TaskException {
        Optional<PodSpec> podSpecOptional = TaskUtils.getPodSpec(serviceSpec, taskInfo);
        if (podSpecOptional.isPresent()) {
            // Find the TaskSpec for the TaskInfo
            int index = CommonTaskUtils.getIndex(taskInfo);
            PodInstance podInstance = new DefaultPodInstance(podSpecOptional.get(), index);
            Optional<TaskSpec> taskSpecOptional = TaskUtils.getTaskSpec(podInstance, taskInfo.getName());
            if (taskSpecOptional.isPresent()) {
                ResourceSet resourceSet = taskSpecOptional.get().getResourceSet();

                List<String> taskNamesToUpdate = podInstance.getPod().getTasks().stream()
                        .filter(taskSpec -> !taskSpec.getName().equals(taskSpecOptional.get().getName()))
                        .filter(taskSpec -> taskSpec.getResourceSet().equals(resourceSet))
                        .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                        .collect(Collectors.toList());

                logger.info("Updating resources for tasks: {}", taskNamesToUpdate);
                List<Protos.TaskInfo> taskInfosToUpdate = taskNamesToUpdate.stream()
                        .map(taskName -> stateStore.fetchTask(taskName))
                        .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                        .map(taskInfoOptional -> taskInfoOptional.get())
                        .collect(Collectors.toList());

                List<String> taskIds = taskInfosToUpdate.stream()
                        .map(taskInfo1 -> taskInfo1.getTaskId().getValue())
                        .collect(Collectors.toList());
                logger.info("Found corresponding TaskInfos: {}", taskIds);

                List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
                for (Protos.TaskInfo taskInfoToUpdate : taskInfosToUpdate) {
                    updatedTaskInfos.add(
                            Protos.TaskInfo.newBuilder(taskInfoToUpdate)
                                    .clearResources()
                                    .addAllResources(taskInfo.getResourcesList())
                                    .build());
                }

                stateStore.storeTasks(updatedTaskInfos);
            }
        }
    }
}
