package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;

/**
 * A {@link PlacementRule} which performs no filtering. Just returns the offers it's given as-is.
 */
public class PassthroughRule implements PlacementRule {

    private static final PassthroughRule INSTANCE = new PassthroughRule();

    public static PassthroughRule getInstance() {
        return INSTANCE;
    }

    @Override
    public Offer filter(Offer offer) {
        return offer;
    }

    @Override
    public String toString() {
        return "PassthroughRule{}";
    }
}
