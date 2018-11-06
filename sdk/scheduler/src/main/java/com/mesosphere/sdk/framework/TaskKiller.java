package com.mesosphere.sdk.framework;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskID;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;

/**
 * This class implements reliable task killing.  Mesos does not provide reliable task killing.
 * This class repeatedly attempts to kill a task until Mesos declares it has been killed or until
 * Mesos doesn't know anything about this task.
 */
public final class TaskKiller {
  private static final Logger LOGGER = LoggingUtils.getLogger(TaskKiller.class);

  /**
   * This lock must be obtained before accessing {@code TASKS_TO_KILL}, {@code executor},
   * or {@code executorEnabled}.
   */
  private static final ReadWriteLock INTERNAL_LOCK =
      CycleDetectingLockUtils.newLock(true, TaskKiller.class);

  private static final Lock RLOCK = INTERNAL_LOCK.readLock();

  private static final Lock RWLOCK = INTERNAL_LOCK.writeLock();

  private static final Duration KILL_INTERVAL = Duration.ofSeconds(5);

  private static final Set<TaskID> TASKS_TO_KILL = new HashSet<>();

  /**
   * After the first task kill, this executor will be created and will periodically reissue kill
   * invocations for any tasks which haven't produced a dead or unknown status. We do this
   * because the Mesos kill command is best-effort.
   */
  private static ScheduledExecutorService executor;

  /**
   * Whether the above executor should be running. Only disabled for tests.
   */
  private static boolean executorEnabled = true;

  private TaskKiller() {
    // Do not instantiate
  }

  /**
   * Resets the {@link TaskKiller}'s internal state for tests.
   *
   * @param executorEnabledInternal whether the background kill executor should be enabled,
   *                                should only be disabled in tests
   */
  @VisibleForTesting
  public static void reset(boolean executorEnabledInternal) throws InterruptedException {
    RWLOCK.lock();
    try {
      if (executor != null) {
        executor.shutdownNow();
        executor.awaitTermination(KILL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
      }
      executor = null;

      TASKS_TO_KILL.clear();

      executorEnabled = executorEnabledInternal;
    } finally {
      RWLOCK.unlock();
    }
  }

  /**
   * Calling this method will cause the referenced TaskID to be reliably killed.  An attempt to kill the task will be
   * immediately made.  Continued periodic attempts will be made to kill the task until Mesos indicates that it has
   * killed the Task or doesn't recongize the task.
   *
   * <p>This is still not a guarantee that a task has been killed.  Mesos may not know about a particular TaskID at
   * any given time.
   *
   * @param taskId the TaskID of the Task to be killed.
   */
  public static void killTask(TaskID taskId) {
    // In order to update a podinstance its normal to kill all tasks in a pod.
    // Sometimes a task hasn't been launched ever but it has been recorded for
    // resource reservation footprint reasons, and therefore doesn't have a TaskID yet.
    if (taskId.getValue().isEmpty()) {
      LOGGER.warn("Attempted to kill empty TaskID.");
      return;
    }

    RWLOCK.lock();
    try {
      TASKS_TO_KILL.add(taskId);
      LOGGER.info("Enqueued kill of task: {}, {} tasks to kill: {}",
          taskId.getValue(),
          TASKS_TO_KILL.size(),
          TASKS_TO_KILL.stream().map(TaskID::getValue).collect(Collectors.toList()));

      // Initialize the executor if enabled and not already running.
      if (executor == null && executorEnabled) {
        LOGGER.info(
            "Initializing scheduled executor with an interval of {}s",
            KILL_INTERVAL.getSeconds());
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(
            TaskKiller::killAllTasks,
            KILL_INTERVAL.toMillis(),
            KILL_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS);
      }
    } finally {
      RWLOCK.unlock();
    }

    // Finally, try invoking the task kill (if driver is set).
    killTaskInternal(taskId);
  }

  /**
   * Updates the list of tasks scheduled to be killed to reflect an incoming task status.
   * <p>
   * Returns whether the task should be eligible for another kill operation. This is intended to break a
   * Kill->Status->Kill->Status->... loop where Mesos returns TASK_LOST+REASON_RECONCILIATION when we attempt to kill
   * an unknown task. Example loop:
   * <p>
   * {@code
   * TaskKiller:killTask: Enqueued kill of task: foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * TaskKiller:killTaskInternal: Killing task: foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * FrameworkScheduler:statusUpdate: Received status update for
   * taskId=foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * state=TASK_LOST
   * message=Reconciliation: Task is unknown
   * TaskKiller:update: Completed killing: foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * FrameworkScheduler:statusUpdate: Got unknown task in response to status update, marking task to be killed:
   * foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * TaskKiller:killTask: Enqueued kill of task: foo__node-0-node__d9e95ff5-caff-48a0-b26d-d6d2aabfd28e
   * }
   *
   * @param taskStatus the received task status
   * @return whether the task for this status is eligible for another kill request
   */
  public static boolean update(Protos.TaskStatus taskStatus) {
    if (!isDead(taskStatus)) {
      // Task is not dead and can be killed.
      return true;
    }

    RWLOCK.lock();
    try {
      if (TASKS_TO_KILL.remove(taskStatus.getTaskId())) {
        LOGGER.info("Completed killing: {}, {} remaining tasks to kill: {}",
            taskStatus.getTaskId().getValue(),
            TASKS_TO_KILL.size(),
            TASKS_TO_KILL.stream().map(t -> t.getValue()).collect(Collectors.toList()));
        // Task is dead AND was already marked. Refrain from killing again right away to avoid kill loop:
        return false;
      } else {
        LOGGER.warn("Task wasn't expected to be killed: {}", taskStatus.getTaskId().getValue());
        // Task is dead but wasn't scheduled for killing. Scheduling it for a kill shouldn't hurt anything.
        return true;
      }
    } finally {
      RWLOCK.unlock();
    }
  }

  @VisibleForTesting
  static void killAllTasks() {
    Set<TaskID> copy;
    RLOCK.lock();
    try {
      copy = new HashSet<>(TASKS_TO_KILL);
    } finally {
      RLOCK.unlock();
    }

    for (TaskID taskId : copy) {
      killTaskInternal(taskId);
    }
  }

  private static void killTaskInternal(TaskID taskId) {
    LOGGER.info("Killing task: {}", taskId.getValue());
    Driver.getInstance().killTask(taskId);
  }

  private static boolean isDead(Protos.TaskStatus taskStatus) {
    switch (taskStatus.getState()) {
      case TASK_KILLING:
      case TASK_RUNNING:
      case TASK_STAGING:
      case TASK_STARTING:
        return false;
      default:
        return true;
    }
  }
}
