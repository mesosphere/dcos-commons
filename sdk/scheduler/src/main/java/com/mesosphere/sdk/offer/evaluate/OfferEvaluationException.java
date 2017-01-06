package com.mesosphere.sdk.offer.evaluate;

/**
 * Exception for errors encountered during the offer evaluation process.
 */
public class OfferEvaluationException extends Exception {
    public OfferEvaluationException(String s) {
        super(s);
    }
}
