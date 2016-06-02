package org.apache.mesos.reconciliation;

import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.Set;

/**
 * Interface for Reconciler.
 */
public interface Reconciler {

    /**
     * Starts reconciliation.
     *
     * @param tasks The current set of unreconciled tasks.
     */
    void start(final Collection<TaskStatus> tasks);

    /**
     * Used to pass the SchedulerDriver to the reconciler. It provides the
     * implementation with a chance to query the Mesos Master for TaskStatus.
     * This method SHOULD be invoked on every TaskStatus update and every
     * offer sequence.
     *
     * @param driver The ShcedulerDriver instance that the Reconciler should
     *               use to query the Mesos master for TaskStatus.
     */
    void reconcile(final SchedulerDriver driver);

    /**
     * Used to update the Reconciler with current task status.
     *
     * @param status The TaskStatus used to update the Reconciler.
     */
    void update(final TaskStatus status);

    /**
     * @return True if reconciliation is complete.
     */
    boolean isReconciled();

    /**
     * @return Return the set of unreconciled task id's as strings.
     */
    Set<String> remaining();

    /**
     * Force reconciliation to a complete state.
     */
    void forceComplete();
}
