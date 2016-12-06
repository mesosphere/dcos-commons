package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

/**
 * This interface encapsulates both a recommended Mesos {@link Operation} to be performed and an
 * {@link Offer} on which the {@link Operation} should be performed.
 */
public interface OfferRecommendation {
    /**
     * Returns the operation which should be performed in regards to the specified {@link Offer}
     * returned by {@link #getOffer()}.
     */
    Operation getOperation();

    /**
     * Returns the Offer upon which the {@link Operation} returned by {@link #getOperation()}
     * should be performed.
     */
    Offer getOffer();
}
