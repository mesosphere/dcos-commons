package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.List;

/**
 * PlanCoordinator job is to perform co-ordination of offers among configured {@code PlanManager}s.
 */
public interface PlanCoordinator {
    /**
     * Provides offers to each {@code PlanManager} for processing. Keeps tracks of dirtied offers and assets.
     *
     * @return List of OfferID's that were used for scheduling operations.
     */
    Collection<Protos.OfferID> processOffers(final SchedulerDriver driver, final List<Protos.Offer> offers);
}
