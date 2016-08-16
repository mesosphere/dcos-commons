package org.apache.mesos.scheduler.recovery.monitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;

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
public class TimedFailureMonitor implements FailureMonitor {
    private static final Log log = LogFactory.getLog(TimedFailureMonitor.class);
    // This map stores the time when we first noticed the failure
    private final HashMap<TaskID, Date> firstFailureDetected;
    private final Duration durationUntilFailed;

    /**
     * Creates a new {@link FailureMonitor} that waits for at least a specified duration before deciding that the task
     * has failed.
     *
     * @param durationUntilFailed The minimum amount of time which must pass before a stopped Task can be considered
     *                            failed.
     */
    public TimedFailureMonitor(Duration durationUntilFailed) {
        this.firstFailureDetected = new HashMap<>();
        this.durationUntilFailed = durationUntilFailed;
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
    public boolean hasFailed(TaskInfo terminatedTask) {
        Date taskLaunchedTime;
        synchronized (firstFailureDetected) {
            if (!firstFailureDetected.containsKey(terminatedTask.getTaskId())) {
                firstFailureDetected.put(terminatedTask.getTaskId(), new Date());
            }
            taskLaunchedTime = firstFailureDetected.get(terminatedTask.getTaskId());
        }

        Date taskExpiredTime = new Date(taskLaunchedTime.getTime() + durationUntilFailed.toMillis());
        Date now = new Date();
        log.info("Looking at " + terminatedTask.getName() + " launchHappened at " + taskLaunchedTime + ", expires at "
                + taskExpiredTime + " which is " + now.after(taskExpiredTime));
        return now.after(taskExpiredTime);
    }
}
