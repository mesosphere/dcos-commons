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
 * Default scheduler. See docs in {@link StageScheduler} interface.
 */
public class DefaultStageScheduler implements StageScheduler {

    private static final Logger logger = LoggerFactory
            .getLogger(DefaultStageScheduler.class);
    private final OfferAccepter offerAccepter;

    @Inject
    public DefaultStageScheduler(OfferAccepter offerAccepter) {
        this.offerAccepter = offerAccepter;
    }

    @Override
    public List<Protos.OfferID> resourceOffers(SchedulerDriver driver,
            List<Protos.Offer> offers, Block block) {
        List<Protos.OfferID> acceptedOffers = new ArrayList<>();

        if (block == null) {
            logger.warn("No block to process.");
            return acceptedOffers;
        }
        if (!block.isPending()) {
            logger.info("Ignoring resource offers for block: {} status: {}",
                    block.getName(), Block.getStatus(block));
            return acceptedOffers;
        }

        logger.info("Processing resource offers for block: {}", block.getName());
        OfferRequirement offerReq = block.start();
        if (offerReq == null) {
            logger.info("No OfferRequirement for block: {}", block.getName());
            return acceptedOffers;
        }

        // Block has returned an OfferRequirement to process. Find offers which match the
        // requirement and accept them, if any are found:
        List<OfferRecommendation> recommendations = new OfferEvaluator(
                offerReq).evaluate(offers);
        if (!recommendations.isEmpty()) {
            // complain that we're not finding suitable offers. out
            // of space on the cluster?:
            logger.warn(
                    "Unable to find any offers which fulfill requirement provided by block: {}: {}",
                    block.getName(), offerReq);
        } else {
            acceptedOffers = offerAccepter.accept(driver,
                    recommendations);
            // notify block of offer outcome:
            block.updateOfferStatus(!acceptedOffers.isEmpty());
        }

        return acceptedOffers;
    }
}
