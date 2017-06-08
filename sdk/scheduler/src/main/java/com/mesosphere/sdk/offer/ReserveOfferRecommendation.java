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
        // The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
        // information which is not appropriate for the RESERVE operation.  It is filtered out here.
        if (resource.hasDisk()) {
            DiskInfo diskInfo = resource.getDisk().toBuilder()
                    .clearVolume()
                    .build();
            resource = resource.toBuilder().setDisk(diskInfo).build();
        }
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
}
