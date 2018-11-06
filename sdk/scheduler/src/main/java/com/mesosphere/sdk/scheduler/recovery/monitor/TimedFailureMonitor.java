package com.mesosphere.sdk.scheduler.recovery.monitor;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;

/**
 * Implements a {@link FailureMonitor} with a time-based policy.
 * <p>
 * Note that, for safety reasons, this only sets a lower bound on when task is determined failed. Since during an outage
 * system clocks can be accidentally misconfigured (for instance, when adding new nodes), we cannot rely on system time
 * (since we might underestimate the wait), and so we must reset our clock from zero when the framework restarts. This
 * unfortunately means that if the framework is also being frequently restarted, this detector may never trigger. A
 * monotonic clock built on ZooKeeper could solve this, by recording each passing second, so that we only need to rely
 * on the fact that the clock proceeds at 1 second per second, rather than on the clocks being synchronized across
 * machines.
 */
public class TimedFailureMonitor extends DefaultFailureMonitor {
  private static final Logger logger = LoggingUtils.getLogger(TimedFailureMonitor.class);

  // This map stores the time when we first noticed the failure
  private final HashMap<Protos.TaskID, Date> firstFailureDetected;

  private final Duration durationUntilFailed;

  private final StateStore stateStore;

  private final ConfigStore<ServiceSpec> configStore;

  /**
   * Creates a new {@link FailureMonitor} that waits for at least a specified duration before deciding that the task
   * has failed.
   *
   * @param durationUntilFailed The minimum amount of time which must pass before a stopped Task can be considered
   *                            failed.
   */
  public TimedFailureMonitor(
      Duration durationUntilFailed,
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore)
  {
    this.firstFailureDetected = new HashMap<>();
    this.durationUntilFailed = durationUntilFailed;
    this.stateStore = stateStore;
    this.configStore = configStore;
  }

  /**
   * Determines whether the given task has failed, by tracking the time delta between the first observed failure and
   * the current time.
   * <p>
   * The first time a task is noticed to be failed, we record that time into a map, keyed by the task's {@link
   * TaskID}. Then, we return true if at least the configured amount of time has passed since then.
   *
   * @param terminatedTask The task that stopped and might be failed
   * @return true if the task has been stopped for at least the configured interval
   */
  @Override
  public boolean hasFailed(Protos.TaskInfo terminatedTask) {
    if (super.hasFailed(terminatedTask)) {
      return true;
    }

    Date taskLaunchedTime;
    synchronized (firstFailureDetected) {
      taskLaunchedTime = firstFailureDetected.get(terminatedTask.getTaskId());
      if (taskLaunchedTime == null) {
        taskLaunchedTime = new Date();
        firstFailureDetected.put(terminatedTask.getTaskId(), taskLaunchedTime);
      }
    }

    Date taskExpiredTime = new Date(taskLaunchedTime.getTime() + durationUntilFailed.toMillis());

    boolean isExpired = new Date().after(taskExpiredTime);
    logger.info("Task {} launch happened at {}, expires at {}, expired={}",
        terminatedTask.getName(), taskLaunchedTime, taskExpiredTime, isExpired);
    if (isExpired) {
      try {
        PodInstance podInstance = TaskUtils.getPodInstance(configStore, terminatedTask);
        FailureUtils.setPermanentlyFailed(stateStore, podInstance);
      } catch (TaskException e) {
        logger.error("Failed to get pod instance to mark as failed.", e);
      }
    }
    return isExpired;
  }
}
