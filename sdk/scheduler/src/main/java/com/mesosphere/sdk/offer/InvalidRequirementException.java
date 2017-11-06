package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.SpecVisitorException;

/**
 * An exception which describes an error in a provided set of Offer Requirements.
 */
public class InvalidRequirementException extends SpecVisitorException {
    public InvalidRequirementException(Throwable ex) {
        super(ex);
    }

    public InvalidRequirementException(String msg) {
        super(msg);
    }

    public InvalidRequirementException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
