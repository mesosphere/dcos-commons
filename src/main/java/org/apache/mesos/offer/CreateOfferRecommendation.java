package org.apache.mesos.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code CREATE} Operation.
 */
public class CreateOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;

    public CreateOfferRecommendation(Offer offer, Resource resource) {
        this.offer = offer;
        this.operation = new OperationBuilder()
                .setType(Operation.Type.CREATE)
                .setCreate(Arrays.asList(resource))
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
