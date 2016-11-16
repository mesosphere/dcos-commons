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
 * A {@link PlanManager} is an {@link Observable}.  It notifies its observers when its plan changes.
 */
public interface PlanManager extends Observer, Observable {
    /**
     * @return the Plan being managed.
     */
    Plan getPlan();

    /**
     * Determines the next {@link Step }that should be considered for scheduling. {@link Step}s that are being selected
     * by other PlanManager are provided as {@code dirtiedAssets} as a hint to this PlanManager to assist with
     * scheduling.
     *
     * @param dirtyAssets Other {@link Step}s/assets that are already selected for scheduling.
     * @return An Optional Step that can be scheduled or an empty Optional when there is no {@link Step} to schedule.
     *         For ex: the chosen {@link Step }to schedule is being worked on by other PlanManager(s), etc.
     */
    Collection<? extends Step> getCandidates(Collection<String> dirtyAssets);

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
