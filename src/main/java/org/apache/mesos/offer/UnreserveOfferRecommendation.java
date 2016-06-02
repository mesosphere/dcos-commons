package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * UnreserveOfferRecommendation.
 * This Recommendation encapsulates a Mesos UNRESERVE Operation
 */
public class UnreserveOfferRecommendation implements OfferRecommendation {
    private OperationBuilder builder;
    private Offer offer;
    private Resource resource;

    public UnreserveOfferRecommendation(Offer offer, Resource resource) {
      this.offer = offer;
      this.resource = resource;

      builder = new OperationBuilder();
      builder.setType(Operation.Type.UNRESERVE);
    }

    public Operation getOperation() {
        builder.setUnreserve(Arrays.asList(getUnreservedResource()));
        return builder.build();
    }

    public Offer getOffer() {
        return offer;
    }

    private Resource getUnreservedResource() {
      Resource.Builder resBuilder = Resource.newBuilder(resource);
      resBuilder.clearDisk();
      resBuilder.clearRevocable();
      return resBuilder.build();
    }
}
