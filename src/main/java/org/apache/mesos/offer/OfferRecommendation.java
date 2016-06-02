package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

/**
 * The OfferRecommendation interface.
 * It encapsulates both the recommended Mesos Operation to be performed
 * and the Offer on which the Operation should be performed.
 */
public interface OfferRecommendation {
  Operation getOperation();

  Offer getOffer();
}
