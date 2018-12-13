package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code RESERVE} Operation.
 */
public class ReserveOfferRecommendation implements OfferRecommendation {
  private final Protos.Offer offer;

  private final Protos.Offer.Operation operation;

  public ReserveOfferRecommendation(Protos.Offer offer, Protos.Resource resource) {
    this.offer = offer;
    this.operation = Protos.Offer.Operation.newBuilder()
        .setType(Protos.Offer.Operation.Type.RESERVE)
        .setReserve(
            Protos.Offer.Operation.Reserve.newBuilder().addResources(getReservedResource(resource))
        )
        .build();
  }

  /**
   * The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
   * information which is not appropriate for the RESERVE operation.  It is filtered out here.
   */
  private static Protos.Resource getReservedResource(Protos.Resource resource) {
    // The resource passed in is the fully completed Resource which will be launched.  This may include volume/disk
    // information which is not appropriate for the RESERVE operation.  It is filtered out here.
    Protos.Resource.Builder resBuilder = Protos.Resource.newBuilder(resource);
    if (resBuilder.hasDisk() && resBuilder.getDisk().hasSource()) {
      // Mount volume: Copy disk, but without 'persistence' nor 'volume' fields
      resBuilder.setDisk(Protos.Resource.DiskInfo.newBuilder(resBuilder.getDisk())
          .clearPersistence()
          .clearVolume());
    } else {
      // Root volume: Clear the disk.
      resBuilder.clearDisk();
    }
    resBuilder.clearRevocable();
    return resBuilder.build();
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
}
