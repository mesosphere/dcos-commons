package com.mesosphere.sdk.offer;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.framework.Driver;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The OfferAccepter extracts the Mesos Operations encapsulated by the OfferRecommendation and accepts Offers with those
 * Operations.
 */
public class OfferAccepter {
    private static final Logger LOGGER = LoggingUtils.getLogger(OfferAccepter.class);
    /**
     * Tell Mesos to consider unused resources as refused for 1 second.
     */
    private static final Protos.Filters FILTERS = Protos.Filters.newBuilder().setRefuseSeconds(1).build();

    public void accept(List<OfferRecommendation> recommendations) {
        if (CollectionUtils.isEmpty(recommendations)) {
            return;
        }

        // Group recommendations by agent: Mesos requires that acceptOffers() only applies to a single agent at a time.
        // Note that ORDERING IS IMPORTANT:
        //    The resource lifecycle is RESERVE -> CREATE -> DESTROY -> UNRESERVE
        //    Therefore we must preserve ordering within each per-agent set of operations.
        final Map<String, List<OfferRecommendation>> recsByAgent = groupByAgent(recommendations);
        for (Map.Entry<String, List<OfferRecommendation>> agentRecs : recsByAgent.entrySet()) {
            Collection<Protos.Offer.Operation> operations = new ArrayList<>();
            Collection<Protos.OfferID> offerIds = new HashSet<>();
            for (OfferRecommendation rec : agentRecs.getValue()) {
                if (!rec.getOperation().isPresent()) {

                    continue;
                }
                // Note: We ensure that we only include the offer ids for recommendations with operations to perform:
                operations.add(rec.getOperation().get());
                offerIds.add(rec.getOfferId());
            }

            int skippedOperations = operations.size() - agentRecs.getValue().size();
            if (skippedOperations != 0) {
                LOGGER.info("Skipping {} recommendations with no operation", skippedOperations);
            }
            logOperations(agentRecs.getKey(), offerIds, operations);
            Driver.getInstance().acceptOffers(offerIds, operations, FILTERS);
        }
    }

    /**
     * Groups recommendations by agent, while preserving their existing order.
     */
    @VisibleForTesting
    protected static Map<String, List<OfferRecommendation>> groupByAgent(List<OfferRecommendation> recommendations) {
        // Use TreeMap for consistent ordering. Not required but simplifies testing, and nice to have consistent output.
        final Map<String, List<OfferRecommendation>> recommendationsByAgent = new TreeMap<>();
        for (OfferRecommendation recommendation : recommendations) {
            final String agentId = recommendation.getAgentId().getValue();
            List<OfferRecommendation> agentRecommendations = recommendationsByAgent.get(agentId);
            if (agentRecommendations == null) {
                agentRecommendations = new ArrayList<>();
                recommendationsByAgent.put(agentId, agentRecommendations);
            }
            agentRecommendations.add(recommendation);
        }
        return recommendationsByAgent;
    }

    private static void logOperations(
            String agentId, Collection<Protos.OfferID> offerIds, Collection<Protos.Offer.Operation> operations) {
        LOGGER.info("Accepting {} offer{} for agent {} with {} operation{}: {}",
                offerIds.size(),
                offerIds.size() == 1 ? "" : "s",
                agentId,
                operations.size(),
                operations.size() == 1 ? "" : "s",
                offerIds.stream().map(Protos.OfferID::getValue).collect(Collectors.toSet()));
        for (Protos.Offer.Operation op : operations) {
            LOGGER.info("  {}", TextFormat.shortDebugString(op));
        }
    }
}
