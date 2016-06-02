package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * DestroyOfferRecommendation.
 * This Recommendation encapsulates a Mesos DESTROY Operation
 */
public class DestroyOfferRecommendation implements OfferRecommendation {
    private OperationBuilder builder;
    private Offer offer;
    private Resource resource;

    public DestroyOfferRecommendation(Offer offer, Resource resource) {
      this.offer = offer;
      this.resource = resource;

      builder = new OperationBuilder();
      builder.setType(Operation.Type.DESTROY);
    }

    public Operation getOperation() {
        builder.setDestroy(Arrays.asList(getDestroyedResource()));
        return builder.build();
    }

    public Offer getOffer() {
        return offer;
    }

    private Resource getDestroyedResource() {
      Resource.Builder resBuilder = Resource.newBuilder(resource);
      resBuilder.clearRevocable();
      return resBuilder.build();
    }
}
