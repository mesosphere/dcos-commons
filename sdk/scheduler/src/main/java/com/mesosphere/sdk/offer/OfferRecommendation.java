package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

/**
 * This interface encapsulates both a recommended Mesos {@link Protos.Offer.Operation} to be performed and an
 * {@link Protos.Offer} on which the {@link Protos.Offer.Operation} should be performed.
 */
public interface OfferRecommendation {
    /**
     * Returns the operation which should be performed in regards to the specified {@link Protos.Offer}
     * returned by {@link #getOffer()}.
     */
    Protos.Offer.Operation getOperation();

    /**
     * Returns the Offer upon which the {@link Protos.Offer.Operation} returned by {@link #getOperation()}
     * should be performed.
     */
    Protos.Offer getOffer();
}
