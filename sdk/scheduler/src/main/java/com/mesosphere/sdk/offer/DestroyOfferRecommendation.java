package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Optional;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code DESTROY} Operation.
 */
public class DestroyOfferRecommendation implements UninstallRecommendation {
  private final Protos.Offer offer;

  private final Protos.Offer.Operation operation;

  private final Protos.Resource resource;

  public DestroyOfferRecommendation(Protos.Offer offer, Protos.Resource resource) {
    this.offer = offer;
    this.operation = Protos.Offer.Operation.newBuilder()
        .setType(Protos.Offer.Operation.Type.DESTROY)
        .setDestroy(Protos.Offer.Operation.Destroy.newBuilder()
            .addAllVolumes(Arrays.asList(Protos.Resource.newBuilder(resource)
                .clearRevocable()
                .build())))
        .build();
    this.resource = resource;
  }

  @Override
  public Optional<Protos.Offer.Operation> getOperation() {
    return Optional.of(operation);
  }

  @Override
  public Protos.OfferID getOfferId() {
    return offer.getId();
  }

  @Override
  public Protos.SlaveID getAgentId() {
    return offer.getSlaveId();
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

  @Override
  public Protos.Resource getResource() {
    return resource;
  }

}
