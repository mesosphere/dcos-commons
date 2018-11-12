package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class provides commonly used utilities for offer handling.
 */
public final class OfferUtils {

  private OfferUtils() {
  }

  /**
   * Filters out Offers which have pending Operations against Mesos, and returns a list of any remaining offers.
   *
   * @param offers          An {@link org.apache.mesos.Protos.Offer} collection containing both ACCEPTED and
   *                        UNACCEPTED offers
   * @param recommendations A collection of offer recommendations, which hold accepted offers
   * @return Offers that are not ACCEPTED yet, or an empty list if there are no UNACCEPTED offers left
   */
  public static List<Protos.Offer> filterOutAcceptedOffers(
      Collection<Protos.Offer> offers, Collection<? extends OfferRecommendation> recommendations)
  {
    // Only filter out offers which specifically have operations to be sent to Mesos. If an offer just led to some
    // internal bookkeeping updates then don't consider it "used".
    Set<Protos.OfferID> usedOfferIds = recommendations.stream()
        .filter(rec -> rec.getOperation().isPresent())
        .map(rec -> rec.getOfferId())
        .collect(Collectors.toSet());
    return offers.stream()
        .filter(offer -> !usedOfferIds.contains(offer.getId()))
        .collect(Collectors.toList());
  }
}
