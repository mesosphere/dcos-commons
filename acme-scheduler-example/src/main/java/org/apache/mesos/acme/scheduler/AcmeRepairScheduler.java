package org.apache.mesos.acme.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.plan.Block;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class AcmeRepairScheduler {

  public List<Protos.OfferID> resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers, Block block) {
    List<Protos.OfferID> acceptedOffers = new ArrayList<Protos.OfferID>();

    /*
    Requires a lot of code specific to acme
    likely for terminated status of tasks saved in a state store
     */
    return acceptedOffers;
  }

}
