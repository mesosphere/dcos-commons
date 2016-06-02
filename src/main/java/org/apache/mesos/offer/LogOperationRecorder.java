package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used as an offer acceptor.  Provides offer logging.
 */
public class LogOperationRecorder implements OperationRecorder {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void record(Protos.Offer.Operation operation, Protos.Offer offer) throws Exception {
    logger.info("Offer: " + offer);
    logger.info("Operation: " + operation);
  }
}
