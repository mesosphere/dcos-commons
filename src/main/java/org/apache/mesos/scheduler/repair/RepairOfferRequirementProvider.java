package org.apache.mesos.scheduler.repair;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.repair.monitor.FailureMonitor;

import java.util.Optional;

/**
 * Implementions of this know how to relaunch tasks from a framework when those tasks crash or fail.
 * <p>
 * The method {@link RepairOfferRequirementProvider#getReplacementOfferRequirement(TaskInfo)} is called once a given
 * task crashes. The implementation should look at the given {@link TaskInfo} in order to know which task it's
 * replacing, and where that task's resources & data have been reserved.
 * <p>
 * The method {@link RepairOfferRequirementProvider#maybeGetNewOfferRequirement(String, Block)} is called once the
 * framework's {@link FailureMonitor} decides that a task has permanently failed, and we should try to find a new
 * location to run it. Typicaly, this means that if the task represented a database, the data on that node is
 * permanently lost.
 */
public interface RepairOfferRequirementProvider {
    /**
     * Possibly returns an {@link OfferRequirement} that will replace a missing task.
     * <p>
     * Implementations should not try to relaunch the current {@link Block}'s task.
     *
     * @param targetConfigName The current configuration we're moving towards
     * @param currentBlock     The current {@link Block} being executed
     * @return {@link OfferRequirement} to relaunch a failed task, or none if all tasks are present
     * @throws InvalidRequirementException
     * @throws ConfigStoreException
     */
    Optional<OfferRequirement> maybeGetNewOfferRequirement(String targetConfigName, Block currentBlock)
            throws InvalidRequirementException, ConfigStoreException;

    /**
     * Computes a new {@link OfferRequirement} that will relaunch-in-place the given {@link TaskInfo}.
     * <p>
     * This will only be called when the argument is in a terminal state, before the {@link FailureMonitor} decides that
     * task is definitely gone forever. The implementation should ensure it's reusing the resources that were reserved
     * by the given {@literal terminatedTask}.
     *
     * @param terminatedTask The task that has stopped and needs to be relaunched.
     * @return {@link OfferRequirement} to relaunch the stopped task in place
     * @throws InvalidRequirementException
     */
    OfferRequirement getReplacementOfferRequirement(TaskInfo terminatedTask) throws InvalidRequirementException;
}
