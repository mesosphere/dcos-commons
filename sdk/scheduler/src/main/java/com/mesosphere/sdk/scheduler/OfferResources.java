package com.mesosphere.sdk.scheduler;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Pairs an offer with a collection of resources within that offer that should be destroyed and/or unreserved.
 */
public class OfferResources {

  /**
   * The parent offer which holds the {@code resources}.
   */
  private final Protos.Offer offer;

  /**
   * The resources which should be destroyed and/or unreserved.
   */
  private final Collection<Protos.Resource> resources;

  public OfferResources(Protos.Offer offer) {
    this.offer = offer;
    this.resources = new ArrayList<>();
  }

  public Protos.Offer getOffer() {
    return offer;
  }

  public Collection<Protos.Resource> getResources() {
    return resources;
  }

  /**
   * Convenience method for {@code getResources().addAll(resources)}.
   *
   * @return {@code this}
   */
  public OfferResources addAll(Collection<Protos.Resource> addResources) {
    this.resources.addAll(addResources);
    return this;
  }

  /**
   * Convenience method for {@code getResources().add(resource)}.
   *
   * @return {@code this}
   */
  public OfferResources add(Protos.Resource resource) {
    this.resources.add(resource);
    return this;
  }

  @Override
  public String toString() {
    return String.format("offer[%s]%nresources%s",
        TextFormat.shortDebugString(offer),
        resources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
  }
}
