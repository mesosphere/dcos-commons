package org.apache.mesos.offer;

/**
 * An exception which describes an error in a provided set of Offer Requirements.
 */
public class InvalidRequirementException extends Exception {
    public InvalidRequirementException(Throwable cause) {
        super(cause);
    }

    public InvalidRequirementException(String msg) {
        super(msg);
    }

    public InvalidRequirementException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
