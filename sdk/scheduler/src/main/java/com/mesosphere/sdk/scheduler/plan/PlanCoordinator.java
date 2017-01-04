package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.scheduler.Observable;
import com.mesosphere.sdk.scheduler.Observer;

import java.util.Collection;
import java.util.List;

/**
 * PlanCoordinator's job is to coordinate offers among configured {@link PlanManager}s.
 *
 * A {@link PlanCoordinator} is an {@link Observer}.  It either has operations to perform or it doesn't, and it updates
 * its observers when this state changes.
 */
public interface PlanCoordinator extends Observable {
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
