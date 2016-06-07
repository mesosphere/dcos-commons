package org.apache.mesos.storage;

/**
 * An exception thrown by the {@link StorageAccess} API.
 */
public class StorageException extends Exception {
  StorageException(String message) {
    super(message);
  }

  StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
