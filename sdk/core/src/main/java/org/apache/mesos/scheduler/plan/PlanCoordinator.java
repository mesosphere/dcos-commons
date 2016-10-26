package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.Observable;

import java.util.Collection;
import java.util.List;

/**
 * PlanCoordinator's job is to coordinate offers among configured {@code PlanManager}s.
 *
 * A {@code PlanCoordinator} is an {@Observer}.  It either has operations to perform or it doesn't, and it updates its
 * observers when this state changes.
 */
public interface PlanCoordinator extends Observable {
    /**
     * Provides offers to each {@code PlanManager} for processing. Keeps tracks of dirtied offers and assets.
     *
     * @return List of OfferID's that were used for scheduling operations.
     */
    Collection<Protos.OfferID> processOffers(final SchedulerDriver driver, final List<Protos.Offer> offers);

    /**
     * @return True if this {@PlanCoordinator} has operations to perform.
     */
    boolean hasOperations();
}
