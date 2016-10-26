package org.apache.mesos.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

/**
 * The OperationRecorder is an Interface required by the OfferAccepter.
 * This interface is intended to allow Framework authors an opportunity to record
 * in write-ahead fashion any Operations they are about to perform to some persistent
 * storage location.
 */
public interface OperationRecorder {
  void record(Operation operation, Offer offer) throws Exception;
}
