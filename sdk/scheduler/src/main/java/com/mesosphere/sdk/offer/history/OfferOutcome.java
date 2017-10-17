package com.mesosphere.sdk.offer.history;

import org.apache.mesos.Protos;

public class OfferOutcome {
    private final String podInstanceName;
    private final boolean pass;
    private final Protos.Offer offer;
    private final String outcome;

    public OfferOutcome(String podInstanceName, boolean pass, Protos.Offer offer, String outcome) {
        this.podInstanceName = podInstanceName;
        this.pass = pass;
        this.offer = offer;
        this.outcome = outcome;
    }

    @Override
    public String toString() {
        return "offer:\n" + offer.toString() + "\n\noutcome" + outcome;
    }
}
