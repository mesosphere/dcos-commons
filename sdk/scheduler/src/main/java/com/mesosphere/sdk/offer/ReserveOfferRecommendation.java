package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code RESERVE} Operation.
 */
public class ReserveOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;

    public ReserveOfferRecommendation(Offer offer, Resource.Builder resource) {
        resource = getReservedResource(resource);
        this.offer = offer;
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.RESERVE)
                .setReserve(Operation.Reserve.newBuilder().addResources(resource))
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

    /**
     * The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
     * information which is not appropriate for the RESERVE operation.  It is filtered out here.
     */
    private static Resource.Builder getReservedResource(Resource.Builder resourceBuilder) {
        // The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
        // information which is not appropriate for the RESERVE operation.  It is filtered out here.
        if (resourceBuilder.hasDisk() && resourceBuilder.getDisk().hasSource()) {
            // Mount volume: Copy disk, but without 'persistence' nor 'volume' fields
            resourceBuilder.setDisk(DiskInfo.newBuilder(resourceBuilder.getDisk())
                    .clearPersistence()
                    .clearVolume());
        } else {
            // Root volume: Clear the disk.
            resourceBuilder.clearDisk();
        }
        resourceBuilder.clearRevocable();
        return resourceBuilder;
    }
}
