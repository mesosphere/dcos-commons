package org.apache.mesos.acme.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.acme.state.AcmeStateService;
import org.apache.mesos.offer.OperationRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class PersistentOperationRecorder implements OperationRecorder {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public PersistentOperationRecorder(AcmeStateService acmeState) {
  }

  @Override
  public void record(Protos.Offer.Operation operation, Protos.Offer offer) throws Exception {
    /**
     * this is where you would record the task initially in the state store.
     */
  }
}
