package com.mesosphere.sdk.scheduler.plan;

import org.slf4j.LoggerFactory;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Common utility methods for {@link PlanManager}s.
 */
public class PlanUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanUtils.class);

    private PlanUtils() {
        // do not instantiate
    }
    
    public static boolean allHaveStatus(Status status, Collection<? extends Element> elements) {
        return elements.stream().allMatch(element -> element.getStatus() == status);
    }

    public static boolean anyHaveStatus(Status status, Collection<? extends Element> elements) {
        return elements.stream().anyMatch(element -> element.getStatus() == status);
    }

    public static List<Offer> filterAcceptedOffers(List<Offer> offers, Collection<OfferID> acceptedOfferIds) {
        return offers.stream().filter(offer -> !acceptedOfferIds.contains(offer.getId())).collect(Collectors.toList());
    }
}
