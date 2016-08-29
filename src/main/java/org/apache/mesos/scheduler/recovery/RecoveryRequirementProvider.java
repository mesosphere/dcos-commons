package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;

import java.util.List;

/**
 * Implementations of this know how to relaunch tasks from a framework when those tasks crash or fail.
 * <p>
 * The method {@link RecoveryRequirementProvider#getTransientRecoveryOfferRequirements(List)} is called once a
 * given Task crashes. The implementation should look at the given {@link List<TaskInfo>} in order to know which Tasks
 * it's replacing, and where that task's resources & data have been reserved.
 * <p>
 * The method {@link RecoveryRequirementProvider#getPermanentRecoveryOfferRequirements(List)} is called once the
 * framework's {@link FailureMonitor} decides that a task has permanently failed, and a new location is needed to run
 * it. Typically, this means that if the task represented a database node, the data on that node is permanently lost.
 */
public interface RecoveryRequirementProvider {
    /**
     * Returns a {@link List<RecoveryRequirement>} that will relaunch Tasks which encountered a transient error.  This
     * method should provide OfferRequirements which consume previously reserved Resources. This will only be called
     * when the arguments are in a terminal state, before the {@link FailureMonitor} decides that Tasks are definitely
     * gone forever.
     * @param stoppedTasks The list of Tasks which have encountered a transient failure.
     */
    List<RecoveryRequirement> getTransientRecoveryOfferRequirements(List<TaskInfo> stoppedTasks)
            throws InvalidRequirementException;

    /**
     * Returns a {@link List<RecoveryRequirement>} that will replace Tasks whose Resources are no longer available given
     * the {@link List<TaskInfo>}.
     *
     * @param failedTasks The list of Tasks which have encountered a permanent failure.
     */
    List<RecoveryRequirement> getPermanentRecoveryOfferRequirements(List<TaskInfo> failedTasks)
            throws InvalidRequirementException;
}
