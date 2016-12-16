package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;

import java.util.*;

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
        final List<OfferRecommendation> recommendations = resourceCleaner.evaluate(offers);

        // Recommendations should be grouped by agent, as Mesos enforces processing of acceptOffers Operations
        // that belong to a single agent.
        final Map<Protos.SlaveID, List<OfferRecommendation>> recommendationsGroupedByAgents =
                        groupRecommendationsByAgent(recommendations);

        final List<OfferID> processedOffers = new ArrayList<>(offers.size());
        for (Map.Entry<Protos.SlaveID, List<OfferRecommendation>> entry : recommendationsGroupedByAgents.entrySet()) {
            processedOffers.addAll(offerAccepter.accept(driver, recommendationsGroupedByAgents.get(entry.getKey())));
        }

        return processedOffers;
    }

    /**
     * Groups recommendations by agent.
     *
     * Visibility is protected to enable testing.
     */
    protected Map<Protos.SlaveID, List<OfferRecommendation>> groupRecommendationsByAgent(
                    List<OfferRecommendation> recommendations) {
        final Map<Protos.SlaveID, List<OfferRecommendation>> recommendationsGroupedByAgents = new HashMap<>();

        for (OfferRecommendation recommendation : recommendations) {
            final Protos.SlaveID agentId = recommendation.getOffer().getSlaveId();

            if (!recommendationsGroupedByAgents.containsKey(agentId)) {
                recommendationsGroupedByAgents.put(agentId, new ArrayList<>());
            }

            final List<OfferRecommendation> agentRecommendations = recommendationsGroupedByAgents.get(agentId);
            agentRecommendations.add(recommendation);
            recommendationsGroupedByAgents.put(agentId, agentRecommendations);
        }

        return recommendationsGroupedByAgents;
    }
}
