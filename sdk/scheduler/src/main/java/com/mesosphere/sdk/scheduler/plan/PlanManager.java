package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos.TaskStatus;
import com.mesosphere.sdk.scheduler.Observable;
import com.mesosphere.sdk.scheduler.Observer;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.Collection;
import java.util.Set;

/**
 * PlanManager is the management interface for {@link Plan}s.  Its main purpose is to apply {@link Strategy} objects to
 * {@link Element}s.
 *
 * A {@link PlanManager} is an {@link Observable}.  It notifies its observers when its {@link Plan} state changes.
 */
public interface PlanManager extends Observer, Observable {
    /**
     * @return the Plan being managed by this instance
     */
    Plan getPlan();

    /**
     * Determines the next {@link Step}s that should be considered for scheduling. {@link Step}s that are being selected
     * by other {@link PlanManager}s are provided as {@code dirtyAssets} as a hint to this {@link PlanManager} to assist
     * with scheduling.
     *
     * @param dirtyAssets Other {@link Step}s/assets that are already claimed for scheduling elsewhere
     * @return One or more {@link Step}s that can be scheduled or an empty Collection when there is no {@link Step} to
     *         schedule, which may happen if for example all incomplete {@link Step}s are already being worked on by
     *         other {@link PlanManager}s
     */
    Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets);

    /**
     * Notifies constituent elements of TaskStatus updates.
     *
     * @param status A TaskStatus from Mesos.
     */
    void update(TaskStatus status);

    /**
     * Returns a {@link Set} of assets that are dirty, i.e. being worked upon by the {@link Plan} that this
     * {@link PlanManager} is working on.
     *
     * @return A {@link Set} containing assets that are dirty
     */
    Set<PodInstanceRequirement> getDirtyAssets();
}
