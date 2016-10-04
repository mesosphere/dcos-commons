package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.List;

/**
 * PlanCoordinator.
 */
public interface PlanCoordinator {
    /**
     *
     *
     * @return List of dirtied offers.
     */
    Collection<Protos.OfferID> processOffers(final SchedulerDriver driver, final List<Protos.Offer> offers);
}
