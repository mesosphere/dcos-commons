package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;

import java.util.Arrays;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code RESERVE} Operation.
 */
public class ReserveOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;

    public ReserveOfferRecommendation(Offer offer, Resource resource) {
        this.offer = offer;
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.RESERVE)
                .setReserve(Operation.Reserve.newBuilder()
                        .addAllResources(Arrays.asList(Resource.newBuilder(getReservedResource(resource)).build())))
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

    private static Resource getReservedResource(Resource resource) {
        Resource.Builder resBuilder = Resource.newBuilder(resource);
        if (resBuilder.hasDisk() && resBuilder.getDisk().hasSource()) {
            // Mount volume: Copy disk, but without 'persistence' nor 'volume' fields
            resBuilder.setDisk(DiskInfo.newBuilder(resBuilder.getDisk())
                    .clearPersistence()
                    .clearVolume());
        } else {
            // Root volume: Clear the disk.
            resBuilder.clearDisk();
        }
        resBuilder.clearRevocable();
        return resBuilder.build();
    }
}
