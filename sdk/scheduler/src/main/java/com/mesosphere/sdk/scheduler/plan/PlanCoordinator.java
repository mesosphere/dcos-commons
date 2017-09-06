package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * PlanCoordinator's job is to coordinate offers among configured {@link PlanManager}s.
 *
 * its observers when this state changes.
 */
public interface PlanCoordinator {
    List<Step> getCandidates();

    /**
     * Provides offers to each {@link PlanManager} for processing. Keeps tracks of dirtied offers and assets.
     *
     * @return List of OfferID's that were used for scheduling operations.
     */
    Collection<Protos.OfferID> processOffers(final SchedulerDriver driver, final List<Protos.Offer> offers);

    /**
     * @return True if this {@link PlanCoordinator} has operations to perform.
     */
    boolean hasOperations();

    /**
     * @return The {@link PlanManager}s which the PlanCoordinator coordinates.
     */
    Collection<PlanManager> getPlanManagers();
}
