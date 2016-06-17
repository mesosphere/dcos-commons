package org.apache.mesos.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code UNRESERVE} Operation.
 */
public class UnreserveOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;

    public UnreserveOfferRecommendation(Offer offer, Resource resource) {
        this.offer = offer;
        this.operation = new OperationBuilder()
                .setType(Operation.Type.UNRESERVE)
                .setUnreserve(Arrays.asList(Resource.newBuilder(resource)
                        .clearDisk()
                        .clearRevocable()
                        .build()))
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
