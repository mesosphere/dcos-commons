package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

import java.util.List;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for Offer.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds Offer objects.
 */
public class OfferBuilder {

  private Protos.Offer.Builder builder = Protos.Offer.newBuilder();

  public OfferBuilder(String offerId, String frameworkId, String slaveId, String hostname) {
    setOfferId(offerId);
    setFrameworkId(frameworkId);
    setSlaveId(slaveId);
    setHostname(hostname);
  }

  public OfferBuilder setOfferId(String id) {
    builder.setId(createOfferID(id));
    return this;
  }

  public OfferBuilder setFrameworkId(String id) {
    builder.setFrameworkId(FrameworkInfoUtil.createFrameworkId(id));
    return this;
  }

  public OfferBuilder setSlaveId(String id) {
    builder.setSlaveId(SlaveUtil.createSlaveId(id));
    return this;
  }

  public OfferBuilder setHostname(String host) {
    builder.setHostname(host);
    return this;
  }

  public OfferBuilder addResource(Protos.Resource resource) {
    builder.addResources(resource);
    return this;
  }

  public OfferBuilder addAllResources(List<Protos.Resource> resourceList) {
    builder.addAllResources(resourceList);
    return this;
  }


  public OfferBuilder addAttribute(Protos.Attribute attribute) {
    builder.addAttributes(attribute);
    return this;
  }

  public Protos.Offer build() {
    return builder.build();
  }

  /**
   * intentional leak for extensions beyond this builder.
   *
   * @return
   */
  public Protos.Offer.Builder builder() {
    return builder;
  }

  public static Protos.Offer createOffer(Protos.FrameworkID frameworkID,
    Protos.OfferID offerID, Protos.SlaveID slaveID, String hostname) {
    return Protos.Offer.newBuilder()
      .setId(offerID)
      .setFrameworkId(frameworkID)
      .setSlaveId(slaveID)
      .setHostname(hostname)
      .build();
  }

  public static Protos.Offer createOffer(String frameworkID, String offerID, String slaveID, String hostname) {
    return createOffer(FrameworkInfoUtil.createFrameworkId(frameworkID),
      createOfferID(offerID), SlaveUtil.createSlaveId(slaveID), hostname);
  }

  public static Protos.OfferID createOfferID(String offerID) {
    return Protos.OfferID.newBuilder().setValue(offerID).build();
  }
}
