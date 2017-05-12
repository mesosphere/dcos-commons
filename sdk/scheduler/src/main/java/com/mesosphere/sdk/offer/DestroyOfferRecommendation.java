package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;

import java.util.Arrays;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code DESTROY} Operation.
 */
public class DestroyOfferRecommendation implements UninstallRecommendation {
    private final Offer offer;
    private final Operation operation;
    private final Resource resource;

    public DestroyOfferRecommendation(Offer offer, Resource resource) {
        this.offer = offer;
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.DESTROY)
                .setDestroy(Operation.Destroy.newBuilder()
                        .addAllVolumes(Arrays.asList(Resource.newBuilder(resource)
                                .clearRevocable()
                                .build())))
                .build();
        this.resource = resource;
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

    @Override
    public Resource getResource() {
        return resource;
    }

}
