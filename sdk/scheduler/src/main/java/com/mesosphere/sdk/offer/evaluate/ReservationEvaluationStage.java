package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class evaluates an offer for remaining reserved resources in the offer that have only been partially claimed by
 * the given {@link OfferRequirement}, and creates an {@link UnreserveOfferRecommendation} for each so that they are
 * freed for general consumption.
 */
public class ReservationEvaluationStage implements OfferEvaluationStage {
    private final Set<String> resourceIds;

    public ReservationEvaluationStage(Collection<String> resourceIds) {
        this.resourceIds = new HashSet<>(resourceIds);
    }

    @Override
    public void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        Map<String, MesosResource> reservedResources = mesosResourcePool.getReservedPool();
        for (Map.Entry<String, MesosResource> entry : reservedResources.entrySet()) {
            if (resourceIds.contains(entry.getKey())) {
                Protos.Resource unreserveResource = ResourceUtils.setResourceId(
                        entry.getValue().getResource(), entry.getKey());
                offerRecommendationSlate.addUnreserveRecommendation(
                        new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), unreserveResource));
            }
        }
    }
}
