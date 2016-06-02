package org.apache.mesos.offer;

import java.util.List;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;

/**
 * This scheduler performs UNRESERVE and DESTROY operations on resources which are identified
 * as unexpected by the ResourceCleaner. 
 */
public class ResourceCleanerScheduler {
  private ResourceCleaner resourceCleaner;
  private OfferAccepter offerAccepter;

  public ResourceCleanerScheduler(
      ResourceCleaner resourceCleaner,
      OfferAccepter offerAccepter) {

    this.resourceCleaner = resourceCleaner;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    List<OfferRecommendation> recommendations = resourceCleaner.evaluate(offers);
    return offerAccepter.accept(driver, recommendations);
  }
}
