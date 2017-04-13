package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;

import java.util.Arrays;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code UNRESERVE} Operation.
 */
public class UnreserveOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;

    public UnreserveOfferRecommendation(Offer offer, Resource resource) {
        this.offer = offer;
        Resource.Builder resourceBuilder = resource.toBuilder();

        // If non-root disk resource, we want to clear ALL fields except for the field indicating the disk source.
        if (resource.hasDisk() && resource.getDisk().hasSource()) {
            resource = resourceBuilder.setDisk(
                    Resource.DiskInfo.newBuilder()
                            .setSource(resource.getDisk().getSource()))
                    .build();
        } else {
            resource = resourceBuilder.clearDisk().clearRevocable().build();
        }

        this.operation = Operation.newBuilder()
                .setType(Operation.Type.UNRESERVE)
                .setUnreserve(Operation.Unreserve.newBuilder()
                        .addAllResources(Arrays.asList(resource)))
                .build();
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public Offer getOffer() {
        return offer;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
