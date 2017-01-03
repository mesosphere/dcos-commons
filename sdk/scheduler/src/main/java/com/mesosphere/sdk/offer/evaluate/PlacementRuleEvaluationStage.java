package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that its resources meet the
 * constraints imposed by the supplied {@link com.mesosphere.sdk.offer.constrain.PlacementRule}.
 */
public class PlacementRuleEvaluationStage implements OfferEvaluationStage {
    private static final Logger logger = LoggerFactory.getLogger(PlacementRuleEvaluationStage.class);

    private Collection<Protos.TaskInfo> deployedTasks;

    public PlacementRuleEvaluationStage(Collection<Protos.TaskInfo> deployedTasks) {
        this.deployedTasks = deployedTasks;
    }

    @Override
    public void evaluate(
            MesosResourcePool offerResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) {
        if (offerRequirement.getPlacementRuleOptional().isPresent()) {
            Protos.Offer offer = offerRequirement.getPlacementRuleOptional().get().filter(
                    offerResourcePool.getOffer(), offerRequirement, deployedTasks);
            offerResourcePool.update(offer);

            int originalCount = offerResourcePool.getOffer().getResourcesCount();
            int filteredCount = offer.getResourcesCount();

            if (filteredCount == originalCount) {
                logger.info(
                        "- Fully passed placement constraint, {} resources remain for evaluation: {}",
                        filteredCount, offer.getId().getValue());
            } else if (filteredCount > 0) {
                logger.info(
                        "- {}: Partially passed placement constraint, {} of {} resources remain for evaluation: {}",
                        filteredCount, originalCount, offer.getId().getValue());
            } else {
                logger.info(
                        "- {}: Failed placement constraint for all {} resources, removed from resource evaluation",
                        originalCount, offer.getId().getValue());
            }
        }
    }
}
