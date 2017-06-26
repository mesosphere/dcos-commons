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

    public ReserveOfferRecommendation(Offer offer, Resource resource) {
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
    private static Resource getReservedResource(Resource resource) {
        // The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
        // information which is not appropriate for the RESERVE operation.  It is filtered out here.
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
