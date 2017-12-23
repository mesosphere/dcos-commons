package com.mesosphere.sdk.reconciliation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link Reconciler}. See {@link Reconciler} for docs.
 */
public class DefaultReconciler implements Reconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReconciler.class);

    // Exponential backoff between explicit reconcile requests: minimum 8s, maximum 30s
    private static final int MULTIPLIER = 2;
    private static final long BASE_BACKOFF_MS = 4000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final AtomicBoolean isImplicitReconciliationTriggered = new AtomicBoolean(false);
    // NOTE: Access to 'unreconciled' must be protected by a lock against 'unreconciled'.
    private final Map<String, TaskStatus> unreconciled = new HashMap<>();
    private final StateStore stateStore;

    private long lastRequestTimeMs;
    private long backOffMs;

    public DefaultReconciler(StateStore stateStore) {
        this.stateStore = stateStore;
        resetTimerValues();
    }

    @Override
    public void start() {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        try {
            taskStatuses.addAll(stateStore.fetchStatuses());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TaskStatuses for reconciliation with exception: ", e);
        }

        synchronized (unreconciled) {
            for (TaskStatus status : taskStatuses) {
                if (!TaskUtils.isTerminal(status)) {
                    unreconciled.put(status.getTaskId().getValue(), status);
                }
            }
            // even if the scheduler thinks no tasks are launched, we should still always perform
            // implicit reconciliation:
            isImplicitReconciliationTriggered.set(false);
            LOGGER.info("Added {} unreconciled tasks to reconciler: {} tasks to reconcile: {}",
                    taskStatuses.size(), unreconciled.size(), unreconciled.keySet());
        }
    }

    /**
     * This function is expected to be called repeatedly. It executes the following pseudocode
     * across multiple calls:
     * <code>
     * unreconciledList = tasksKnownByScheduler // provided by the StateStore
     * while (!unreconciledList.isEmpty()) {
     *   // explicit reconciliation (PHASE 1)
     *   if (timerSinceLastCallExpired) {
     *     driver.reconcile(unreconciledList);
     *   }
     * }
     * driver.reconcile(emptyList); // implicit reconciliation (PHASE 2)
     * </code>
     */
    @Override
    public void reconcile(final SchedulerDriver driver) {
        if (isImplicitReconciliationTriggered.get()) {
            // PHASE 3: implicit reconciliation has been triggered, we're done
            return;
        }

        /**
         * NOTE: It is important not to hold a lock when making calls to {@link driver}.  The driver
         * holds a lock internally when making calls to the Scheduler.  If both the scheduler and driver
         * follow the pattern of acquiring a lock and making a remote call then deadlocks occur.  To avoid
         * this we unilaterally enforce that we do not hold any locks while making calls to {@link driver}.
         */
        Collection<TaskStatus> tasksToReconcile = Collections.emptyList();
        synchronized (unreconciled) {
            if (!unreconciled.isEmpty()) {
                final long nowMs = getCurrentTimeMillis();
                // PHASE 1: unreconciled tasks remain: trigger explicit reconciliation against the
                // remaining known tasks originally reported by the StateStore.
                if (nowMs >= lastRequestTimeMs + backOffMs) {
                    // update timer values for the next reconcile() call:
                    lastRequestTimeMs = nowMs;
                    long newBackoff = backOffMs * MULTIPLIER;
                    backOffMs = Math.min(newBackoff > 0 ? newBackoff : 0, MAX_BACKOFF_MS);

                    // pass a COPY of the list, in case driver is doing anything with it..:
                    tasksToReconcile = ImmutableList.copyOf(unreconciled.values());
                } else {
                    // timer has not expired yet, do nothing for this call
                    LOGGER.info("Too soon since last explicit reconciliation trigger. Waiting at "
                            + "least {}ms before next explicit reconciliation ({} remaining tasks)",
                            lastRequestTimeMs + backOffMs - nowMs, unreconciled.size());
                    return;
                }
            }
        }

        if (tasksToReconcile.isEmpty()) {
            // PHASE 2: no unreconciled tasks remain, trigger a single implicit reconciliation,
            // where we get the list of all tasks currently known to Mesos.
            LOGGER.info("Triggering implicit final reconciliation of all tasks");

            // reset the timer values in case we're started again in the future
            resetTimerValues();
            isImplicitReconciliationTriggered.set(true); // enter PHASE 3/complete

        } else {
            LOGGER.info("Triggering explicit reconciliation of {} remaining tasks, next "
                            + "explicit reconciliation in {}ms or later",
                    unreconciled.size(), backOffMs);
        }

        driver.reconcileTasks(tasksToReconcile);
    }

    @Override
    public void update(final Protos.TaskStatus status) {
        synchronized (unreconciled) {
            if (unreconciled.isEmpty()) {
                return;
            }
            // we've gotten a task status update callback. mark this task as reconciled, if needed
            unreconciled.remove(status.getTaskId().getValue());
            LOGGER.info("Reconciled task: {} ({} remaining tasks)",
                    status.getTaskId().getValue(), unreconciled.size());
        }
    }

    @Override
    public boolean isReconciled() {
        return unreconciled.isEmpty();
    }

    /**
     * Returns the list of remaining unreconciled tasks for validation in tests.
     */
    @VisibleForTesting
    Set<String> remaining() {
        synchronized (unreconciled) {
            return ImmutableSet.copyOf(unreconciled.keySet());
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
