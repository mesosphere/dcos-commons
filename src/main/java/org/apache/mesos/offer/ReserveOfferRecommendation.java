package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * ReserveOfferRecommendation.
 * This Recommendation encapsulates a Mesos RESERVE Operation
 */
public class ReserveOfferRecommendation implements OfferRecommendation {
    private OperationBuilder builder;
    private Offer offer;
    private Resource resource;

    public ReserveOfferRecommendation(Offer offer, Resource resource) {
      this.offer = offer;
      this.resource = resource;

      builder = new OperationBuilder();
      builder.setType(Operation.Type.RESERVE);
    }

    public Operation getOperation() {
        builder.setReserve(Arrays.asList(getReservedResource()));
        return builder.build();
    }

    public Offer getOffer() {
        return offer;
    }

    private Resource getReservedResource() {
      Resource.Builder resBuilder = Resource.newBuilder(resource);

      if (resBuilder.hasDisk() && resBuilder.getDisk().hasSource()) {
        DiskInfo.Builder diskBuilder = DiskInfo.newBuilder(resBuilder.getDisk());
        diskBuilder.clearPersistence();
        diskBuilder.clearVolume();
        resBuilder.setDisk(diskBuilder.build());
      } else {
        resBuilder.clearDisk();
      }

      resBuilder.clearRevocable();
      return resBuilder.build();
    }
}
