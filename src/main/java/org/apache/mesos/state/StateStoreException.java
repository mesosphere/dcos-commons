package org.apache.mesos.state;

/**
 * Exception that indicates that there was an issue with storing values
 * in the state store.  The underlying exception is intended to be
 * nested for developer understanding.
 */
public class StateStoreException extends RuntimeException {

  public StateStoreException(String message) {
    super(message);
  }

  public StateStoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
