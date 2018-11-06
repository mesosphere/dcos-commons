package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.StoreTaskInfoRecommendation;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Records the result of launched tasks to persistent storage.
 */
public class PersistentLaunchRecorder {
  private final Logger logger;

  private final StateStore stateStore;

  private final ServiceSpec serviceSpec;

  public PersistentLaunchRecorder(
      StateStore stateStore,
      ServiceSpec serviceSpec,
      Optional<String> namespace)
  {
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.stateStore = stateStore;
    this.serviceSpec = serviceSpec;
  }

  /**
   * Returns {@link Protos.TaskInfo}s which have been updated to contain the provided resource list, overwriting any
   * previous resource information.
   */
  private static Collection<Protos.TaskInfo> updateTasksWithResources(
      Collection<Protos.TaskInfo> taskInfosToUpdate,
      Collection<Protos.Resource> taskResources,
      Optional<Collection<Protos.Resource>> executorResources)
  {
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

  public void record(Collection<OfferRecommendation> offerRecommendations) throws Exception {
    for (OfferRecommendation offerRecommendation : offerRecommendations) {
      if (!(offerRecommendation instanceof StoreTaskInfoRecommendation)) {
        continue;
      }
      Protos.TaskInfo taskInfo =
          ((StoreTaskInfoRecommendation) offerRecommendation).getStateStoreTaskInfo();

      Optional<PodInstance> podInstance = getPodInstance(taskInfo);

      Optional<Protos.TaskStatus> taskStatus = Optional.empty();
      String taskStatusDescription = "";
      if (!taskInfo.getTaskId().getValue().equals("")) {
        // Initialize the task status as TASK_STAGING. In practice we should never actually receive a STAGING
        // status from Mesos so this is effectively an internal stub for the scheduler's own use.
        taskStatusDescription = " with STAGING status";

        Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
            .setTaskId(taskInfo.getTaskId())
            .setState(Protos.TaskState.TASK_STAGING);
        if (taskInfo.hasExecutor()) {
          taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
        }

        taskStatus = Optional.of(taskStatusBuilder.build());
      }

      logger.info("Persisting launch operation{} for {}",
          taskStatusDescription,
          taskInfo.getName());

      podInstance.ifPresent(podInstance1 ->
          updateTaskResourcesWithinResourceSet(podInstance1, taskInfo)
      );
      stateStore.storeTasks(Collections.singletonList(taskInfo));
      taskStatus.ifPresent(taskStatus1 -> stateStore.storeStatus(taskInfo.getName(), taskStatus1));
    }
  }

  @VisibleForTesting
  Optional<PodInstance> getPodInstance(Protos.TaskInfo taskInfo) throws TaskException {
    Optional<PodSpec> podSpecOptional = TaskUtils.getPodSpec(serviceSpec, taskInfo);
    return podSpecOptional.isPresent() ?
        Optional.of(new DefaultPodInstance(
            podSpecOptional.get(),
            new TaskLabelReader(taskInfo).getIndex()
        )) :
        Optional.empty();
  }

  /**
   * This method keeps the resources associated with tasks in the state store up to date, when a task which shares
   * their resource-set is launched.
   *
   * @param podInstance the parent pod associated with the task being launched
   * @param taskInfo    the task being launched
   */
  @VisibleForTesting
  void updateTaskResourcesWithinResourceSet(PodInstance podInstance, Protos.TaskInfo taskInfo) {
    Optional<TaskSpec> taskSpec = TaskUtils.getTaskSpec(podInstance, taskInfo.getName());
    if (!taskSpec.isPresent()) {
      return;
    }

    // Update any other TaskInfos in this resource set to have the same resources:
    Collection<Protos.TaskInfo> taskInfosWithSameResourceSet =
        getOtherTasksInResourceSet(podInstance, taskSpec.get());
    stateStore.storeTasks(updateTasksWithResources(
        taskInfosWithSameResourceSet,
        taskInfo.getResourcesList(),
        taskInfo.hasExecutor() ?
            Optional.of(taskInfo.getExecutor().getResourcesList())
            : Optional.empty()));
  }

  /**
   * Returns a list of other tasks whose pod and resource set match the provided task information.
   * The returned list will avoid including the same task that was provided.
   */
  private Collection<Protos.TaskInfo> getOtherTasksInResourceSet(
      PodInstance podInstance,
      TaskSpec sourceTaskSpec)
  {
    // Find the names of tasks sharing the resource set being used in this launch
    List<String> taskNamesToUpdate = podInstance.getPod().getTasks().stream()
        // Avoid returning sourceTask itself:
        .filter(taskSpec -> !taskSpec.getName().equals(sourceTaskSpec.getName()))
        .filter(taskSpec -> taskSpec.getResourceSet().equals(sourceTaskSpec.getResourceSet()))
        .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
        .collect(Collectors.toList());

    // Fetch any existing matching TaskInfos from the state store
    List<Protos.TaskInfo> taskInfosToUpdate = taskNamesToUpdate.stream()
        .map(stateStore::fetchTask)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());

    if (!taskInfosToUpdate.isEmpty()) {
      List<String> taskIds = taskInfosToUpdate.stream()
          .map(taskInfoToUpdate -> taskInfoToUpdate.getTaskId().getValue())
          .collect(Collectors.toList());
      logger.info(
          "Updating resources for other tasks sharing resource set '{}': names={} => ids={}",
          sourceTaskSpec.getResourceSet().getId(),
          taskNamesToUpdate,
          taskIds
      );
    }

    return taskInfosToUpdate;
  }
}
