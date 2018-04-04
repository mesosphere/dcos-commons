package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class provides commonly used utilities for offer handling.
 */
public class OfferUtils {

    /**
     * Filters out accepted offers and returns back a list of unused offers.
     *
     * @param offers          An {@link org.apache.mesos.Protos.Offer} collection containing both ACCEPTED and
     *                        UNACCEPTED offers
     * @param recommendations A collection of offer recommendations, which hold accepted offers
     * @return Offers that are not ACCEPTED yet, or an empty list if there are no UNACCEPTED offers left
     */
    public static List<Protos.Offer> filterOutAcceptedOffers(
            Collection<Protos.Offer> offers, Collection<? extends OfferRecommendation> recommendations) {
        Set<Protos.OfferID> usedOfferIds = recommendations.stream()
                .map(rec -> rec.getOffer().getId())
                .collect(Collectors.toSet());
        return offers.stream()
                .filter(offer -> !usedOfferIds.contains(offer.getId()))
                .collect(Collectors.toList());
    }
}
