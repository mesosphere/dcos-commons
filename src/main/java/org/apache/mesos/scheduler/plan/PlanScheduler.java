package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.List;

/**
 * Interface for Plan schedulers. Attempts to start {@link Block}s, while fulfilling any
 * {@link OfferRequirement} they provide.
 */
public interface PlanScheduler {
  /**
   * Processes the provided {@code offers} using the provided {@code driver} against the provided
   * {@code block}. {@code block} should be whatever block is currently the next pending block in
   * the current plan, but may also be {@code null}.
   *
   * @return a list of zero or more of the provided offers which were accepted to fulfill offer
   *         requirements returned by the block
   */
  List<Protos.OfferID> resourceOffers(
      SchedulerDriver driver, List<Protos.Offer> offers, Block block);
}
