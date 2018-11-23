package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;

import java.util.Collections;
import java.util.Optional;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code UNRESERVE} Operation.
 */
public class UnreserveOfferRecommendation implements UninstallRecommendation {
  private final Offer offer;

  private final Operation operation;

  private final Resource resource;

  public UnreserveOfferRecommendation(Offer offer, Resource resource) {
    this.offer = offer;
    Resource.Builder resourceBuilder = resource.toBuilder();

    Resource resourceR;
    // If non-root disk resource, we want to clear ALL fields except for the field indicating the disk source.
    if (resource.hasDisk() && resource.getDisk().hasSource()) {
      resourceR = resourceBuilder
          .setDisk(Resource.DiskInfo.newBuilder().setSource(resource.getDisk().getSource()))
          .build();
    } else {
      resourceR = resourceBuilder.clearDisk().clearRevocable().build();
    }

    this.operation = Operation.newBuilder()
        .setType(Operation.Type.UNRESERVE)
        .setUnreserve(
            Operation.Unreserve.newBuilder().addAllResources(Collections.singletonList(resourceR))
        )
        .build();
    this.resource = resourceR;
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
  public Resource getResource() {
    return resource;
  }
}
