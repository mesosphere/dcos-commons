package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.List;

/**
 * Interface for Stage schedulers.
 */
public interface StageScheduler {

  List<Protos.OfferID> resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers, Block block);
}
