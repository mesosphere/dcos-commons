package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

import java.util.Optional;


/**
 * This interface encapsulates both a recommended Mesos {@link Operation} to be performed and an
 * {@link Offer} on which the {@link Operation} should be performed.
 */
public interface OfferRecommendation {

    /**
     * Returns an operation which should be sent to Mesos in response to the offer (as returned by {@link #getOffer()}),
     * or an empty {@link Optional} if nothing should be sent to Mesos. The latter case can be useful for any additional
     * bookkeeping work to be performed following a successful evaluation.
     */
    Optional<Protos.Offer.Operation> getOperation();

    /**
     * Returns the ID of the original Offer that had been evaluated when generating the recommendation.
     */
    Protos.OfferID getOfferId();

    /**
     * Returns the ID of the agent machine that the original Offer belonged to.
     */
    Protos.SlaveID getAgentId();
}
