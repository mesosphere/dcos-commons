package com.mesosphere.sdk.offer.history;

import org.apache.mesos.Protos;

/**
 * Encapsulates the outcome of an offer evaluation in OfferEvaluator.
 */
public class OfferOutcome {
    private final long timestamp;
    private final String podInstanceName;
    private final boolean pass;
    private final Protos.Offer offer;
    private final String outcomeDetails;

    public OfferOutcome(String podInstanceName, boolean pass, Protos.Offer offer, String outcomeDetails) {
        this.timestamp = System.currentTimeMillis();
        this.podInstanceName = podInstanceName;
        this.pass = pass;
        this.offer = offer;
        this.outcomeDetails = outcomeDetails;
    }

    public String getPodInstanceName() {
        return podInstanceName;
    }

    public boolean pass() {
        return pass;
    }

    public Protos.Offer getOffer() {
        return offer;
    }

    public String getOutcomeDetails() {
        return outcomeDetails;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
