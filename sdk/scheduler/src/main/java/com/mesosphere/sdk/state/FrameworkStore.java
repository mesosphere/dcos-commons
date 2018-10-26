package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * A {@link FrameworkStore} stores the Framework ID for the scheduler. The framework ID is required for the scheduler to
 * re-identify itself with Mesos after initial registration.
 * <p>
 * <p>The structure used in the underlying persister is as follows:
 * <br>rootPath/
 * <br>&nbsp;-> FrameworkID
 */
public class FrameworkStore {

  private static final Logger LOGGER = LoggingUtils.getLogger(FrameworkStore.class);

  private static final String FWK_ID_PATH_NAME = "FrameworkID";

  private final Persister persister;

  /**
   * Creates a new {@link FrameworkStore} which uses the provided {@link Persister} to access the Framework ID.
   *
   * @param persister The persister which holds the data
   */
  public FrameworkStore(Persister persister) {
    this.persister = persister;
  }

  /**
   * Stores the FrameworkID for a framework so on Scheduler restart re-registration may occur.
   *
   * @param fwkId FrameworkID to be stored
   * @throws StateStoreException when storing the FrameworkID fails
   */
  public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
    try {
      persister.set(FWK_ID_PATH_NAME, fwkId.toByteArray());
    } catch (PersisterException e) {
      throw new StateStoreException(e, "Failed to store FrameworkID");
    }
  }

  /**
   * Removes any previously stored FrameworkID or does nothing if no FrameworkID was previously stored.
   *
   * @throws StateStoreException when clearing a FrameworkID fails
   */
  public void clearFrameworkId() throws StateStoreException {
    try {
      persister.recursiveDelete(FWK_ID_PATH_NAME);
    } catch (PersisterException e) {
      if (e.getReason() == Reason.NOT_FOUND) {
        // Clearing a non-existent FrameworkID should not result in an exception from us.
        LOGGER.warn("Cleared unset FrameworkID, continuing silently", e);
      } else {
        throw new StateStoreException(e);
      }
    }
  }

  /**
   * Fetches the previously stored FrameworkID, or returns an empty Optional if no FrameworkId was previously stored.
   *
   * @return The previously stored FrameworkID, or an empty Optional indicating the FrameworkID has not been set.
   * @throws StateStoreException when fetching the FrameworkID fails
   */
  public Optional<Protos.FrameworkID> fetchFrameworkId() throws StateStoreException {
    try {
      byte[] bytes = persister.get(FWK_ID_PATH_NAME);
      if (bytes.length > 0) {
        return Optional.of(Protos.FrameworkID.parseFrom(bytes));
      } else {
        throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
            "Empty FrameworkID in '%s'", FWK_ID_PATH_NAME));
      }
    } catch (PersisterException e) {
      if (e.getReason() == Reason.NOT_FOUND) {
        LOGGER.warn("No FrameworkId found at: {}", FWK_ID_PATH_NAME);
        return Optional.empty();
      } else {
        throw new StateStoreException(e);
      }
    } catch (InvalidProtocolBufferException e) {
      throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
    }
  }
}
