package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.OperationBuilder;

import java.util.Arrays;

/**
 * Create OfferRecommendation.
 * This Recommendation encapsulates a Mesos CREATE Operation
 */
public class CreateOfferRecommendation implements OfferRecommendation {
  private OperationBuilder builder;
  private Offer offer;
  private Resource resource;

  public CreateOfferRecommendation(Offer offer, Resource resource) {
    this.offer = offer;
    this.resource = resource;

    builder = new OperationBuilder();
    builder.setType(Operation.Type.CREATE);
  }

  public Operation getOperation() {
    return builder.setCreate(Arrays.asList(resource)).build();
  }

  public Offer getOffer() {
    return offer;
  }
}
