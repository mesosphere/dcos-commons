package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.storage.StorageError.Reason;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
@SuppressWarnings({
    "checkstyle:InnerTypeLast",
    "checkstyle:IllegalCatch",
    "checkstyle:AtclauseOrder",
    "checkstyle:SingleSpaceSeparator",
})
public final class StateStoreUtils {

  private static final Logger LOGGER = LoggingUtils.getLogger(StateStoreUtils.class);

  private static final String UNINSTALLING_PROPERTY_KEY = "uninstalling";

  private static final String LAST_COMPLETED_UPDATE_TYPE_KEY = "last-completed-update-type";

  private static final String PROPERTY_TASK_INFO_SUFFIX = ":task-status";

  private static final byte[] DEPLOYMENT_TYPE = "DEPLOY".getBytes(StandardCharsets.UTF_8);

  private StateStoreUtils() {
    // do not instantiate
  }

  /**
   * Returns the requested property, or an empty array if the property is not present.
   */
  public static byte[] fetchPropertyOrEmptyArray(StateStore stateStore, String key) {
    if (stateStore.fetchPropertyKeys().contains(key)) {
      return stateStore.fetchProperty(key);
    } else {
      return new byte[0];
    }
  }

  /**
   * Returns all {@link Protos.TaskInfo}s associated with the provided {@link PodInstance},
   * or an empty list if none were found.
   *
   * @throws StateStoreException in the event of an IO error other than missing tasks
   */
  public static Collection<Protos.TaskInfo> fetchPodTasks(StateStore stateStore,
                                                          PodInstance podInstance)
      throws StateStoreException
  {
    Collection<String> taskInfoNames = podInstance.getPod().getTasks().stream()
        .map(taskSpec -> CommonIdUtils.getTaskInstanceName(podInstance, taskSpec))
        .collect(Collectors.toList());

    return taskInfoNames.stream()
        .map(name -> stateStore.fetchTask(name))
        .filter(taskInfo -> taskInfo.isPresent())
        .map(taskInfo -> taskInfo.get())
        .collect(Collectors.toList());
  }

  /**
   * Returns the {@link Protos.TaskInfo} corresponding to the provided {@link Protos.TaskStatus}.
   *
   * @return the {@link Protos.TaskInfo} if it is present
   * @throws StateStoreException if no matching {@link Protos.TaskInfo} was found
   */
  public static Protos.TaskInfo fetchTaskInfo(StateStore stateStore, Protos.TaskStatus taskStatus)
      throws StateStoreException
  {
    final String taskName;
    try {
      taskName = CommonIdUtils.toTaskName(taskStatus.getTaskId());
    } catch (TaskException e) {
      throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
    }

    Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
    if (!taskInfoOptional.isPresent()) {
      throw new StateStoreException(Reason.NOT_FOUND, String.format(
          "Failed to find a task with TaskID: %s", taskStatus));
    }
    return taskInfoOptional.get();
  }

  /**
   * TaskInfo and TaskStatus objects referring to the same Task name are not written atomically.
   * It is therefore possible for the states across these elements to become out of sync.
   * While the scheduler process is up they remain in sync.
   * This method produces an initial synchronized state.
   * <p>
   * For example:
   * <ol>
   * <li>TaskInfo(name=foo, id=1) is written</li>
   * <li>TaskStatus(name=foo, id=1, status=RUNNING) is written</li>
   * <li>Task foo is reconfigured/relaunched</li>
   * <li>TaskInfo(name=foo, id=2) is written</li>
   * <li>Scheduler is restarted before new TaskStatus is written</li>
   * <li>Scheduler comes back and sees TaskInfo(name=foo, id=2)
   * and TaskStatus(name=foo, id=1, status=RUNNING)</li>
   * </ol>
   */
  static void repairTaskIDs(StateStore stateStore) {
    Map<String, Protos.TaskStatus> repairedStatuses = new HashMap<>();
    List<Protos.TaskInfo> repairedTasks = new ArrayList<>();

    for (Protos.TaskInfo task : stateStore.fetchTasks()) {
      Optional<Protos.TaskStatus> statusOptional = stateStore.fetchStatus(task.getName());
      if (statusOptional.isPresent()) {
        Protos.TaskStatus status = statusOptional.get();
        if (task.getTaskId().getValue().isEmpty() && TaskUtils.isTerminal(status)) {
          LOGGER.warn(
              "Found empty StateStore taskInfo task {}: task.taskId={}, " +
                  "taskStatus.taskId={} reconciling from taskStatus", task.getName(),
              task.getTaskId(), status.getTaskId());
          repairedStatuses.put(
              task.getName(), status.toBuilder().setState(status.getState()).build());
        }  else if (!status.getTaskId().equals(task.getTaskId())) {
          LOGGER.warn(
              "Found StateStore status inconsistency for task {}: task.taskId={}, " +
                  "taskStatus.taskId={}",
              task.getName(), task.getTaskId(), status.getTaskId());
          repairedTasks.add(task.toBuilder().setTaskId(status.getTaskId()).build());
          repairedStatuses.put(
              task.getName(), status.toBuilder().setState(Protos.TaskState.TASK_FAILED).build());
        }
      } else {
        LOGGER.warn(
            "Found StateStore status inconsistency for task {}: task.taskId={}, no status",
            task.getName(), task.getTaskId());
        Protos.TaskStatus status = Protos.TaskStatus.newBuilder()
            .setTaskId(task.getTaskId())
            .setState(Protos.TaskState.TASK_FAILED)
            .setMessage("Assuming failure for inconsistent TaskIDs")
            .build();
        repairedStatuses.put(task.getName(), status);
      }
    }

    stateStore.storeTasks(repairedTasks);
    repairedStatuses.entrySet().stream()
        .filter(statusEntry -> !statusEntry.getValue().getTaskId().getValue().equals(""))
        .forEach(statusEntry -> stateStore.storeStatus(
            statusEntry.getKey(), statusEntry.getValue()));
  }

  /**
   * Returns the current value of the 'uninstall' property in the provided {@link StateStore}.
   * If the {@link StateStore} was created against a namespaced service,
   * then this returns whether that service is uninstalling.
   */
  public static boolean isUninstalling(StateStore stateStore) throws StateStoreException {
    return fetchBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY);
  }

  /**
   * Sets an 'uninstall' property in the provided {@link StateStore} to {@code true}.
   * If the {@link StateStore} was created against a namespaced service,
   * then this flags that service as uninstalling. This value may be checked
   * using {@link #isUninstalling(StateStore)}.
   */
  public static void setUninstalling(StateStore stateStore) {
    setBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY, true);
  }

  @VisibleForTesting
  protected static boolean fetchBooleanProperty(StateStore stateStore, String propertyName) {
    byte[] bytes = fetchPropertyOrEmptyArray(stateStore, propertyName);
    if (bytes.length == 0) {
      return false;
    } else {
      try {
        return new JsonSerializer().deserialize(bytes, Boolean.class);
      } catch (IOException e) {
        LOGGER.error(String.format("Error converting property '%s' to boolean.", propertyName), e);
        throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
      }
    }
  }

  private static void setBooleanProperty(StateStore stateStore,
                                         String propertyName,
                                         boolean value)
  {
    stateStore.storeProperty(propertyName, new JsonSerializer().serialize(value));
  }

  /**
   * Stores a TaskStatus as a Property in the provided state store.
   */
  public static void storeTaskStatusAsProperty(StateStore stateStore, String taskName,
                                               Protos.TaskStatus taskStatus)
      throws StateStoreException
  {
    stateStore.storeProperty(taskName + PROPERTY_TASK_INFO_SUFFIX, taskStatus.toByteArray());
  }

  /**
   * Returns an Optional<TaskStatus> from the properties in the provided state store for the
   * specified task name.
   */
  public static Optional<Protos.TaskStatus> getTaskStatusFromProperty(StateStore stateStore,
                                                                      String taskName)
  {
    try {
      return Optional.of(Protos.TaskStatus.parseFrom(
          stateStore.fetchProperty(taskName + PROPERTY_TASK_INFO_SUFFIX)));
    } catch (Exception e) {
      // Broadly catch exceptions to handle:
      // Invalid TaskStatuses
      // StateStoreExceptions
      LOGGER.error("Unable to decode TaskStatus for taskName=" + taskName, e);
      return Optional.empty();
    }
  }

  /**
   * Sets whether the service has previously completed deployment.
   */
  public static void setDeploymentWasCompleted(StateStore stateStore) {
    // Optimization: Avoid writing through to curator if possible. We cache reads but not writes.
    if (!getDeploymentWasCompleted(stateStore)) {
      stateStore.storeProperty(LAST_COMPLETED_UPDATE_TYPE_KEY, DEPLOYMENT_TYPE);
    }
  }

  /**
   * Gets whether the service has previously completed deployment. If this is {@code true},
   * then any configuration
   * changes should be treated as an update rather than a new deployment.
   */
  public static boolean getDeploymentWasCompleted(StateStore stateStore) {
    return Arrays.equals(fetchPropertyOrEmptyArray(stateStore, LAST_COMPLETED_UPDATE_TYPE_KEY),
        DEPLOYMENT_TYPE);
  }
}
