package com.mesosphere.sdk.dcos.ca;

/**
 * General Certificate Authority endpoint exception.
 */
public class CAException extends Exception {

    public CAException() {
    }

    public CAException(String message) {
        super(message);
    }

    public CAException(Throwable throwable) {
        super(throwable);
    }

}
