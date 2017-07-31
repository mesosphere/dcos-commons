package com.mesosphere.sdk.offer.evaluate;

/**
 * Any exception thrown during traversal of a {@link com.mesosphere.sdk.specification.PodSpec} by a {@link SpecVisitor}.
 */
public class SpecVisitorException extends Exception {

    public SpecVisitorException(Throwable ex) {
        super(ex);
    }

    public SpecVisitorException(String msg) {
        super(msg);
    }

    public SpecVisitorException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
