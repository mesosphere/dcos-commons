package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

/**
 * This class evaluates an offer for remaining reserved resources in the offer that have only been partially claimed by
 * the given {@link OfferRequirement}, and creates an {@link UnreserveOfferRecommendation} for each so that they are
 * freed for general consumption.
 */
public class ReservationEvaluationStage implements OfferEvaluationStage {
    private static final Logger logger = LoggerFactory.getLogger(ResourceEvaluationStage.class);

    private final Set<String> resourceIds;

    public ReservationEvaluationStage(Collection<String> resourceIds) {
        this.resourceIds = new HashSet<>(resourceIds);
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Map<String, MesosResource> reservedResources = mesosResourcePool.getReservedPool();
        Collection<OfferRecommendation> recommendations = new ArrayList<>();
        for (Map.Entry<String, MesosResource> entry : reservedResources.entrySet()) {
            if (resourceIds.contains(entry.getKey())) {
                logger.info("    Remaining reservation for resource {} unclaimed, generating UNRESERVE operation",
                        TextFormat.shortDebugString(entry.getValue().getResource()));
                Protos.Resource unreserveResource = ResourceUtils.setResourceId(
                        entry.getValue().getResource(), entry.getKey());
                recommendations.add(new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), unreserveResource));
            }
        }
        return pass(this, recommendations, "Added reservation information to offer requirement");
    }
}
