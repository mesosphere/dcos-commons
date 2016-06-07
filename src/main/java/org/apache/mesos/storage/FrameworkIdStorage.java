package org.apache.mesos.storage;

import org.apache.mesos.Protos.FrameworkID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores all persistent state for the Acme Framework.
 */
public class FrameworkIdStorage {

  private static final Logger logger = LoggerFactory.getLogger(FrameworkIdStorage.class);

  private static final String FRAMEWORK_ID_FILE = "/state/framework-id";

  private final StorageAccess storageAccess;

  public FrameworkIdStorage(StorageAccess storageAccess) {
    this.storageAccess = storageAccess;
  }

  /**
   * Attempts to assign the provided {@link FrameworkID} in storage.
   */
  public void setFrameworkId(FrameworkID frameworkId) {
    try {
      storageAccess.set(FRAMEWORK_ID_FILE, frameworkId.toByteArray());
    } catch (StorageException e) {
      logger.error("Failed to store Framework ID in storage", e);
    }
  }

  /**
   * Returns the currently stored {@link FrameworkID} for the scheduler, or {@code null} if none
   * could be retrieved.
   */
  public FrameworkID getFrameworkId() {
    byte[] bytes;
    try {
      bytes = storageAccess.get(FRAMEWORK_ID_FILE);
    } catch (StorageException e) {
      logger.error("Failed to retrieve Framework ID from storage", e);
      return null;
    }
    if (bytes.length == 0) {
      logger.error("Got zero-length Framework ID from storage");
      return null;
    }
    try {
      return FrameworkID.parseFrom(bytes);
    } catch (Exception e) {
      logger.error("Failed to deserialize Framework ID of size " + bytes.length, e);
      return null;
    }
  }
}
