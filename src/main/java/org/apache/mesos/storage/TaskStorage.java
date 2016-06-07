package org.apache.mesos.storage;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OperationRecorder;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 * Stores all persistent state relating to running framework Tasks, in the form of serialized
 * {@link TaskInfo}s.
 */
public class TaskStorage implements Observer, TaskStatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(TaskStorage.class);
  private static final String TASK_DIR = "/state/tasks";

  private final StorageAccess storageAccess;

  public TaskStorage(StorageAccess storageAccess) {
    this.storageAccess = storageAccess;
  }

  @Override
  public void update(Observable ignored, Object obj) {
    TaskStatus taskStatus = (TaskStatus) obj;
    try {
      recordTaskStatus(taskStatus);
    } catch (Exception e) {
      logger.error(String.format("Failed to update TaskStatus '%s'", taskStatus), e);
    }
  }

  @Override
  public Set<Protos.TaskStatus> getTaskStatuses() throws Exception {
    Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();
    for (String taskName : getTaskNames()) {
      taskStatuses.add(getTaskStatus(taskName));
    }
    return taskStatuses;
  }

  /**
   * Attempts to delete the provided task's information.
   */
  public void deleteTask(String taskName) {
    try {
      storageAccess.delete(toTaskRootPath(taskName));
    } catch (StorageException e) {
      logger.error(String.format(
          "Failed to delete '%s' from storage", toTaskRootPath(taskName)), e);
    }
  }

  public List<TaskInfo> getTaskInfos() throws StorageException {
    List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
    for (String taskId : getTaskNames()) {
      try {
        taskInfos.add(getTaskInfo(taskId));
      } catch (StorageException e) {
        logger.error("Failed to retrieve task info for '%s' from storage", e);
      }
    }
    return taskInfos;
  }

  /**
   * Updates any TaskInfos currently referencing an entry in {@code oldTargetConfigs} to instead
   * reference {@code newTargetConfig}. This is used for cleaning up duplicate configs.
   */
  public List<TaskInfo> replaceTargetConfigs(
      Set<String> oldTargetConfigs, String replacementTargetConfig) throws StorageException {
    List<TaskInfo> updatedInfos = new ArrayList<>();
    for (TaskInfo taskInfo : getTaskInfos()) {
      if (oldTargetConfigs.contains(ConfigStorage.getConfigName(taskInfo))) {
        // Update target name, store, and return
        TaskInfo updatedInfo = ConfigStorage.withConfigName(taskInfo, replacementTargetConfig);
        update(null, updatedInfo);
        updatedInfos.add(updatedInfo);
      } else {
        // Leave as-is, and return
        updatedInfos.add(taskInfo);
      }
    }
    return updatedInfos;
  }

  /**
   * Returns the {@link TaskStatus} for the provided {@code taskName}, or returns {@code null} if
   * the {@link TaskStatus} couldn't be retrieved.
   */
  public TaskStatus getTaskStatus(String taskName) {
    byte[] data;
    try {
      data = storageAccess.get(toTaskStatusPath(taskName));
    } catch (StorageException e) {
      logger.error(String.format(
          "Failed to fetch task status '%s' from path '%s'",
          taskName, toTaskStatusPath(taskName)), e);
      return null;
    }
    try {
      return TaskStatus.parseFrom(data);
    } catch (Exception e) {
      logger.error(String.format(
          "Failed to parse task status '%s' from path '%s'",
          taskName, toTaskStatusPath(taskName)), e);
      return null;
    }
  }

  public List<TaskInfo> getTerminatedTaskInfos() throws StorageException {
    List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
    for (String taskName : getTaskNames()) {
      TaskStatus taskStatus = getTaskStatus(taskName);
      if (TaskUtils.isTerminated(taskStatus)) {
        taskInfos.add(getTaskInfo(taskName));
      }
    }
    return taskInfos;
  }

  public OperationRecorder getOperationRecorder() {
    return new PersistentOperationRecorder(this);
  }

  // ---

  private static class PersistentOperationRecorder implements OperationRecorder {

    private final TaskStorage taskStorage;

    public PersistentOperationRecorder(TaskStorage taskStorage) {
      this.taskStorage = taskStorage;
    }

    @Override
    public void record(Protos.Offer.Operation operation, Protos.Offer offer) throws Exception {
      if (operation.getType() == Operation.Type.LAUNCH) {
        taskStorage.recordLaunchTasks(operation.getLaunch().getTaskInfosList());
      }
    }
  }

  private void recordLaunchTasks(List<TaskInfo> taskInfos) {
    for (TaskInfo taskInfo : taskInfos) {
      TaskStatus taskStatus = TaskStatus.newBuilder()
          .setTaskId(taskInfo.getTaskId())
          .setState(TaskState.TASK_STAGING).build();
      try {
        storageAccess.set(toTaskInfoPath(taskInfo.getName()), taskInfo.toByteArray());
        recordTaskStatus(taskStatus);
      } catch (Exception e) {
        logger.error("Failed to record launched task", e);
        // try to keep going with other taskinfos..
      }
    }
  }

  /**
   * Returns the {@link TaskInfo} for the provided {@code taskName}, or returns {@code null} if
   * the {@link TaskInfo} couldn't be retrieved.
   */
  private TaskInfo getTaskInfo(String taskName) throws StorageException {
    byte[] data = storageAccess.get(toTaskInfoPath(taskName));
    try {
      return TaskInfo.parseFrom(data);
    } catch (Exception e) {
      throw new StorageException(String.format(
          "Failed to parse task info '%s' from storage path '%s'",
          taskName, toTaskInfoPath(taskName)), e);
    }
  }

  private List<String> getTaskNames() throws StorageException {
    return storageAccess.list(TASK_DIR);
  }

  private void recordTaskStatus(TaskStatus taskStatus) throws StorageException {
    String statusPath = toTaskStatusPath(TaskUtils.toTaskName(taskStatus.getTaskId()));
    if (!storageAccess.exists(statusPath)
        && !taskStatus.getState().equals(TaskState.TASK_STAGING)) {
      logger.warn(
          "Dropping status update: task status isn't in storage and Status is not STAGING: {}",
          taskStatus);
    } else {
      storageAccess.set(statusPath, taskStatus.toByteArray());
    }
  }

  private String toTaskRootPath(String taskName) {
    return String.format("%s/%s", TASK_DIR, taskName);
  }

  private String toTaskInfoPath(String taskName) {
    return toTaskRootPath(taskName) + "/info";
  }

  private String toTaskStatusPath(String taskName) {
    return toTaskRootPath(taskName) + "/status";
  }
}
