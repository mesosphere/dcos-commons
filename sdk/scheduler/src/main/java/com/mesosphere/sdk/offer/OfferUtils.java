package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.scheduler.Metrics;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides commonly used utilities for offer handling.
 */
public class OfferUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferUtils.class);

    /**
     * Filters out accepted offers and returns back a list of unused offers.
     *
     * @param offers           An {@link org.apache.mesos.Protos.Offer} collection containing both ACCEPTED and
     *                         UNACCEPTED offers.
     * @param acceptedOfferIds A collection of OfferIDs that are already ACCEPTED.
     * @return A List of offers are that not ACCEPTED yet; can be empty if there are no UNACCEPTED offers left.
     */
    public static List<Protos.Offer> filterOutAcceptedOffers(
            List<Protos.Offer> offers,
            Collection<Protos.OfferID> acceptedOfferIds) {
        return offers.stream()
                .filter(offer -> !isOfferAccepted(offer, acceptedOfferIds))
                .collect(Collectors.toList());
    }

    /**
     * Determines if a certain offer is part of the accepted OfferID collection.
     *
     * @param offer            An {@link org.apache.mesos.Protos.Offer} that needs to be checked for acceptance.
     * @param acceptedOfferIds A {@link Collection} of accepted {@link org.apache.mesos.Protos.OfferID}.
     * @return {@code true} if {@link org.apache.mesos.Protos.Offer} is accepted; {@code false} otherwise.
     */
    public static boolean isOfferAccepted(
            Protos.Offer offer,
            Collection<Protos.OfferID> acceptedOfferIds) {
        return acceptedOfferIds.stream()
                .anyMatch(acceptedOfferId -> acceptedOfferId.equals(offer.getId()));
    }

    public static void declineShort(SchedulerDriver driver, Collection<Protos.Offer> unusedOffers) {
        OfferUtils.declineOffers(driver, unusedOffers, Constants.SHORT_DECLINE_SECONDS);
        Metrics.incrementDeclinesShort(unusedOffers.size());
    }

    public static void declineLong(SchedulerDriver driver, Collection<Protos.Offer> unusedOffers) {
        OfferUtils.declineOffers(driver, unusedOffers, Constants.LONG_DECLINE_SECONDS);
        Metrics.incrementDeclinesLong(unusedOffers.size());
    }

    /**
     * Decline unused {@link org.apache.mesos.Protos.Offer}s.
     *
     * @param driver The {@link SchedulerDriver} that will receive the declineOffer() calls
     * @param unusedOffers The collection of Offers to decline
     * @param refuseSeconds The number of seconds for which the offers should be refused
     */
    private static void declineOffers(
            SchedulerDriver driver, Collection<Protos.Offer> unusedOffers, int refuseSeconds) {
        Collection<Protos.OfferID> offerIds = unusedOffers.stream()
                .map(offer -> offer.getId())
                .collect(Collectors.toList());
        LOGGER.info("Declining {} unused offer{} for {} seconds: {}",
                offerIds.size(),
                offerIds.size() == 1 ? "" : "s",
                refuseSeconds,
                offerIds.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));
        final Protos.Filters filters = Protos.Filters.newBuilder()
                .setRefuseSeconds(refuseSeconds)
                .build();
        offerIds.forEach(offerId -> driver.declineOffer(offerId, filters));
    }
}
