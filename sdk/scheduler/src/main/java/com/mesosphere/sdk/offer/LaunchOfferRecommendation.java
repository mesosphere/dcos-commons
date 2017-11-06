package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public abstract class LaunchOfferRecommendation implements OfferRecommendation {
    private final Protos.Offer offer;
    private final boolean shouldLaunch;

    public LaunchOfferRecommendation(
            Protos.Offer offer,
            boolean shouldLaunch) {
        this.offer = offer;
        this.shouldLaunch = shouldLaunch;
    }

    @Override
    public abstract Protos.Offer.Operation getOperation();

    @Override
    public Protos.Offer getOffer() {
        return offer;
    }

    public boolean shouldLaunch() {
        return shouldLaunch;
    }

    /**
     * Returns the {@link Protos.TaskInfo} to be passed to a StateStore upon launch.
     */
    public abstract Protos.TaskInfo getStoreableTaskInfo();

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}

