package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.List;

/**
 * Interface for Plan schedulers. Attempts to start {@link Step}s, while fulfilling any
 * {@link PodInstanceRequirement}s they provide.
 */
public interface PlanScheduler {
    /**
     * Processes the provided {@code Offer}s using the provided {@code SchedulerDriver} against the provided
     * {@link Step}. {@link Step} should be whatever {@link Step} is currently the next pending {@link Step} in
     * the current {@link Plan}, but may also be {@code null}.
     *
     * @return a list of zero or more of the provided offers which were accepted to fulfill offer
     *         requirements returned by the {@link Step}
     */
    Collection<OfferID> resourceOffers(
            final SchedulerDriver driver,
            final List<Offer> offers,
            final Collection<? extends Step> steps);
}
