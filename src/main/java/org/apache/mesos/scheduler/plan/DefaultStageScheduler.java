package org.apache.mesos.scheduler.plan;

import com.google.inject.Inject;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Default scheduler.
 * Attempts to meet the Offer requirements of the block presented, and perform the appropriate Operations
 */
public class DefaultStageScheduler implements StageScheduler {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private OfferAccepter offerAccepter = null;

  @Inject
  public DefaultStageScheduler(OfferAccepter offerAccepter) {
    this.offerAccepter = offerAccepter;
  }

  @Override
  public List<Protos.OfferID> resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers, Block block) {
    List<Protos.OfferID> acceptedOffers = new ArrayList<>();

    if (block != null) {
      logger.info("Processing resource offers for block: " + block.getName());

      if (block.isPending()) {
        OfferRequirement offerReq = block.start();
        if (offerReq != null) {
          OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
          List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
          acceptedOffers = offerAccepter.accept(driver, recommendations);

          if (acceptedOffers.size() > 0) {
            block.setStatus(Status.InProgress);
          } else {
            block.setStatus(Status.Pending);
          }
        } else {
          logger.warn("No OfferRequirement for block: " + block.getName());
        }
      }
    } else {
      logger.warn("No block to process.");
    }

    return acceptedOffers;
  }
}
