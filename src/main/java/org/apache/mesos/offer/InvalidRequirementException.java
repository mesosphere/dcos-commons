package org.apache.mesos.offer;

/**
 * An exception which describes an error in a provided set of Offer Requirements.
 */
public class InvalidRequirementException extends Exception {
    public InvalidRequirementException(Exception ex) {
        super(ex);
    }

    public InvalidRequirementException(String msg) {
        super(msg);
    }

    public InvalidRequirementException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
