package org.apache.mesos.state;

/**
 * Exception that indicates that there was an issue with storing or accessing values in the state
 * store.  The underlying exception from the storage implementation, if any, is intended to
 * nested inside this exception for developer understanding.
 */
public class StateStoreException extends RuntimeException {

    public StateStoreException(Throwable e) {
        super(e);
    }

    public StateStoreException(String message) {
        super(message);
    }

    public StateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
