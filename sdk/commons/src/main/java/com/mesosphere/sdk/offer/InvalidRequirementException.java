package com.mesosphere.sdk.offer;

/**
 * An exception which describes an error in a provided set of Offer Requirements.
 */
public class InvalidRequirementException extends Exception {
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
