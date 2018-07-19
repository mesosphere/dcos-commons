package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskStatus;
import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Synchronizes the service's task state with what Mesos reports, with Mesos as source of truth.
 *
 * This differs from Implicit Reconciliation in two ways:
 * <ul><li>Explicit Reconciliation is tied to a specific set of tasks, and is therefore service-specific rather than
 * framework-wide.</li>
 * <li>While Implicit Reconciliation is run periodically on a timer, Explicit Reconciliation is only performed once on
 * service startup.</li></ul>
 */
public class ExplicitReconciler {

    // Exponential backoff between explicit reconcile requests: minimum 8s, maximum 30s
    private static final int MULTIPLIER = 2;
    private static final long BASE_BACKOFF_MS = 4000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final AtomicBoolean isComplete;
    private final Logger logger;
    private final StateStore stateStore;

    /**
     * This lock must be obtained before using {@code unreconciled}, {@code lastRequestTimeMs}, or {@code backOffMs}.
     */
    private final Lock rlock;
    private final Lock rwlock;

    private final Map<String, TaskStatus> unreconciled;
    private long lastRequestTimeMs;
    private long backOffMs;

    public ExplicitReconciler(StateStore stateStore, Optional<String> namespace, SchedulerConfig schedulerConfig) {
        this.isComplete = new AtomicBoolean(false);
        this.logger = LoggingUtils.getLogger(getClass(), namespace);
        this.stateStore = stateStore;

        ReadWriteLock lock = CycleDetectingLockUtils.newLock(schedulerConfig, ExplicitReconciler.class);
        this.rlock = lock.readLock();
        this.rwlock = lock.writeLock();

        this.unreconciled = new HashMap<>();
        resetTimerValues();
    }

    /**
     * Starts reconciliation against the provided tasks, which should represent what the Scheduler
     * currently knows about task status.
     * <p>
     * NOTE: THIS CALL MUST BE THREAD-SAFE AGAINST OTHER RECONCILER CALLS
     */
    public void start() {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        try {
            taskStatuses.addAll(stateStore.fetchStatuses());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch TaskStatuses for reconciliation with exception: ", e);
        }

        rwlock.lock();
        try {
            for (TaskStatus status : taskStatuses) {
                if (!TaskUtils.isTerminal(status)) {
                    unreconciled.put(status.getTaskId().getValue(), status);
                }
            }
            if (!unreconciled.isEmpty()) {
                // Only resume explicit reconciliation if there's something to reconcile...
                isComplete.set(false);
            }
            resetTimerValues();
            logger.info("Added {} unreconciled task{} to reconciler: {} task{} to reconcile: {}",
                    taskStatuses.size(), taskStatuses.size() == 1 ? "" : "s",
                    unreconciled.size(), unreconciled.size() == 1 ? "" : "s",
                    unreconciled.keySet());
        } finally {
            rwlock.unlock();
        }
    }

    /**
     * This function is expected to be called repeatedly. It executes the following pseudocode
     * across multiple calls:
     * <code>
     * unreconciledList = tasksKnownByScheduler; // provided by the StateStore
     * while (!unreconciledList.isEmpty()) {
     *   // explicit reconciliation (PHASE 1)
     *   if (timerSinceLastCallExpired) {
     *     driver.reconcile(unreconciledList);
     *   }
     * }
     * </code>
     */
    public void reconcile() {
        if (isComplete.get()) {
            // Short-circuit: nothing left to do.
            return;
        }

        /**
         * NOTE: It is important not to hold a lock when making calls to {@link driver}.  The driver
         * holds a lock internally when making calls to the Scheduler.  If both the scheduler and driver
         * follow the pattern of acquiring a lock and making a remote call then deadlocks occur.  To avoid
         * this we unilaterally enforce that we do not hold any locks while making calls to {@link driver}.
         */
        Collection<TaskStatus> tasksToReconcile = Collections.emptyList();
        rwlock.lock();
        try {
            if (!unreconciled.isEmpty()) {
                final long nowMs = getCurrentTimeMillis();
                // Unreconciled tasks remain: trigger explicit reconciliation against the remaining known tasks which
                // were originally found in the StateStore.
                if (nowMs >= lastRequestTimeMs + backOffMs) {
                    // Update timer values for the next reconcile() call:
                    lastRequestTimeMs = nowMs;
                    long newBackoff = backOffMs * MULTIPLIER;
                    backOffMs = Math.min(newBackoff > 0 ? newBackoff : 0, MAX_BACKOFF_MS);

                    // Pass a COPY of the list, in case driver is doing anything with it..:
                    tasksToReconcile = ImmutableList.copyOf(unreconciled.values());
                } else {
                    // Timer has not expired yet, do nothing for this call
                    logger.info("Too soon since last explicit reconciliation trigger. Waiting at "
                            + "least {}ms before sending explicit reconciliation request for {} remaining task{}",
                            lastRequestTimeMs + backOffMs - nowMs,
                            unreconciled.size(),
                            unreconciled.size() == 1 ? "" : "s");
                    return;
                }
            }
            if (!tasksToReconcile.isEmpty()) {
                // Log here while we're still locked, but don't invoke driver until later:
                logger.info("Triggering explicit reconciliation of {} remaining task{}, next "
                                + "explicit reconciliation in {}ms or later",
                        unreconciled.size(), unreconciled.size() == 1 ? "" : "s", backOffMs);
            }
        } finally {
            rwlock.unlock();
        }

        if (tasksToReconcile.isEmpty()) {
            logger.info("Completed explicit reconciliation");
            isComplete.set(true);
        } else {
            Driver.getInstance().reconcileTasks(tasksToReconcile);
        }
    }

    /**
     * Used to update the Reconciler with current task status. This is effectively an asynchronous
     * callback which is triggered by a call to reconcile().
     * <p>
     * NOTE: THIS CALL MUST BE THREAD-SAFE AGAINST OTHER RECONCILER CALLS
     *
     * @param status The TaskStatus used to update the Reconciler.
     */
    public void update(final Protos.TaskStatus status) {
        rwlock.lock();
        try {
            if (unreconciled.isEmpty()) {
                return;
            }
            // we've gotten a task status update callback. mark this task as reconciled, if needed
            unreconciled.remove(status.getTaskId().getValue());
            logger.info("Reconciled task: {} ({} remaining tasks)",
                    status.getTaskId().getValue(), unreconciled.size());
        } finally {
            rwlock.unlock();
        }
    }

    /**
     * Returns whether reconciliation is complete.
     * <p>
     * NOTE: THIS CALL MUST BE THREAD-SAFE AGAINST OTHER RECONCILER CALLS
     */
    public boolean isReconciled() {
        rlock.lock();
        try {
            return unreconciled.isEmpty();
        } finally {
            rlock.unlock();
        }
    }

    /**
     * Returns the list of remaining unreconciled tasks for validation in tests.
     */
    @VisibleForTesting
    Set<String> remaining() {
        rlock.lock();
        try {
            return ImmutableSet.copyOf(unreconciled.keySet());
        } finally {
            rlock.unlock();
        }
    }

    /**
     * Time retrieval broken out into a separate function to allow overriding its behavior in tests.
     */
    @VisibleForTesting
    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    private void resetTimerValues() {
        lastRequestTimeMs = 0;
        backOffMs = BASE_BACKOFF_MS;
    }
}
