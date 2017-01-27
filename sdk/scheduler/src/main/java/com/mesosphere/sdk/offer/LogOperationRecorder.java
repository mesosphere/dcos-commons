package com.mesosphere.sdk.offer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used as an offer acceptor.  Provides offer logging.
 */
public class LogOperationRecorder implements OperationRecorder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        logger.info("Offer: {}", offerRecommendation.getOffer());
        logger.info("Operation: {}", offerRecommendation.getOperation());
    }
}
