package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.scheduler.Observable;
import org.apache.mesos.scheduler.Observer;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.Collection;
import java.util.Set;

/**
 * PlanManager is the management interface for {@link Plan}s.  Its main purpose is to apply {@link Strategy} objects to
 * {@link Element}s.
 *
 * A {@PlanManager} is an {@Observable}.  It notifies its observers when its plan changes.
 */
public interface PlanManager extends Observer, Observable {
    /**
     * @return the Plan being managed.
     */
    Plan getPlan();

    /**
     * Determines the next block that should be considered for scheduling. Blocks that are being selected by other
     * PlanManager are provided as {@code dirtiedAssets} as a hint to this PlanManager to assist with scheduling.
     *
     * @param dirtyAssets Other blocks/assets that are already selected for scheduling.
     * @return An Optional Block that can be scheduled or an empty Optional when there is no block to schedule. For ex:
     *          the chosen block to schedule is being worked on by other PlanManager(s), etc.
     */
    Collection<? extends Block> getCandidates(Collection<String> dirtyAssets);

    /**
     * Notify constituent elements of TaskStatus updates.
     * @param status A TaskStatus from Mesos.
     */
    void update(TaskStatus status);

    /**
     * Returns a {@link Set} of assets that are dirty, i.e. being worked upon by the {@link Plan} that this
     * {@link PlanManager} is working on.
     *
     * @return A {@link Set} containing assets that are dirty.
     */
    Set<String> getDirtyAssets();
}
