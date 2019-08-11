package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.CommonIdUtils;
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
import java.util.Comparator;
import java.util.Iterator;
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
      ServiceSpec serviceSpec)
  {
    this.logger = LoggingUtils.getLogger(getClass());
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
      executorResources.ifPresent(resources -> taskBuilder.getExecutorBuilder()
              .clearResources()
              .addAllResources(resources));
      updatedTaskInfos.add(taskBuilder.build());
    }
    return updatedTaskInfos;
  }

  public void record(Collection<OfferRecommendation> offerRecommendations) throws Exception {
    /**
     *
     * Some of the {@link org.apache.mesos.Protos.TaskInfo} may have duplicate {@link StoreTaskInfoRecommendation}
     * because during evaluation of _each_ step we may decide to either Launch+Store or just Store the TaskInfo.
     * See {@link com.mesosphere.sdk.offer.evaluate.LaunchEvaluationStage}
     *
     * Consider the scenario where
     *  Pod P has only two tasks X and Y.
     *  Step A needs to launch task X.
     *  Step B needs to launch task Y.
     *  In their evaluation pipelines for step A and B:
     *   - Step A would generate Launch+Store for task X and only Store for task Y.
     *     During this step A assumes Y is not launched right away and thus uses "" (empty string) for its task id.
     *   - Step B would generate Launch+Store for task Y and only Store for task X.
     *     During this step B assumes X is not launched right away and thus uses "" (empty string) for its task id.
     *  We do need to do adhere to above behavior because of DCOS-42539.
     *
     * Because of above constraint, we do the following:
     * 1. Filter given {@link OfferRecommendation} by only looking for {@link StoreTaskInfoRecommendation}
     * 2. Persist all {@link StoreTaskInfoRecommendation}s that have empty task ID.
     * 3. After above, persist the rest of {@link StoreTaskInfoRecommendation} that have non empty task ID.
     */
    Iterator<StoreTaskInfoRecommendation> storeTaskRecommendations = offerRecommendations
            .stream()
            .filter(recommendation -> recommendation instanceof StoreTaskInfoRecommendation)
            .map(StoreTaskInfoRecommendation.class::cast)
            .sorted(Comparator.comparingInt(
              r -> r.getStateStoreTaskInfo().getTaskId().getValue().length())
            )
            .iterator();
    while (storeTaskRecommendations.hasNext()) {
      StoreTaskInfoRecommendation offerRecommendation = storeTaskRecommendations.next();
      Protos.TaskInfo taskInfo = offerRecommendation.getStateStoreTaskInfo();
      Optional<PodInstance> podInstance = getPodInstance(taskInfo);

      Optional<Protos.TaskStatus> taskStatus = Optional.empty();
      String taskStatusDescription = "";
      if (!taskInfo.getTaskId().equals(CommonIdUtils.emptyTaskId())) {
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
        .map(taskSpec -> CommonIdUtils.getTaskInstanceName(podInstance, taskSpec))
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
