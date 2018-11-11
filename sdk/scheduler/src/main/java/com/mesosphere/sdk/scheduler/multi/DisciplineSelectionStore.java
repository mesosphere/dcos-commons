package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import jersey.repackaged.com.google.common.base.Joiner;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link DisciplineSelectionStore} stores the list of services selected to receive reservations. This is used in
 * combination with a reservation discipline to determine which services should be receiving reservations at any given
 * time. This storage ensures that the selection remains consistent across scheduler restarts.
 * <p>
 * Includes an internal write-through cache which avoids persister writes if the selected services haven't changed.
 *
 * <p>The structure used in the underlying persister is as follows:
 * <br>rootPath/
 * <br>&nbsp;-> SelectedServices
 */
public class DisciplineSelectionStore {

  private static final String SELECTED_SERVICES_PATH_NAME = "SelectedServices";

  // Service names cannot contain double underscores, so we use double underscores as our delimiter:
  private static final String SERVICE_NAME_DELIMITER = "__";

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final Persister persister;

  // Local cache:
  private Optional<ImmutableSet<String>> serviceNamesCache;

  /**
   * Creates a new {@link DisciplineSelectionStore} which uses the provided {@link Persister} to access the list of
   * selected services.
   *
   * @param persister The persister which holds the data
   */
  public DisciplineSelectionStore(Persister persister) {
    this.persister = persister;
    this.serviceNamesCache = Optional.empty();
  }

  /**
   * Stores the list of services that are selected for reservations.
   *
   * @param serviceNames The service names to be stored
   * @return whether the underlying storage was actually updated
   * @throws PersisterException when storing the selection fails
   */
  public boolean storeSelectedServices(Set<String> serviceNames) throws PersisterException {
    if (serviceNamesCache.isPresent() && serviceNames.equals(serviceNamesCache.get())) {
      // No change.
      return false;
    }
    serviceNamesCache = Optional.of(ImmutableSet.copyOf(serviceNames));
    byte[] data = Joiner.on(SERVICE_NAME_DELIMITER).join(serviceNames).getBytes(CHARSET);
    persister.set(SELECTED_SERVICES_PATH_NAME, data);
    return true;
  }

  /**
   * Fetches the previously stored selected services, or returns an empty Set if none were previously stored.
   *
   * @return The previously stored selected services, or an empty Set if nothing was listed
   * @throws PersisterException when fetching the data fails
   */
  public ImmutableSet<String> fetchSelectedServices() throws PersisterException {
    if (serviceNamesCache.isPresent()) {
      return serviceNamesCache.get();
    }

    byte[] bytes;
    try {
      bytes = persister.get(SELECTED_SERVICES_PATH_NAME);
    } catch (PersisterException e) {
      if (e.getReason() == Reason.NOT_FOUND) {
        serviceNamesCache = Optional.of(ImmutableSet.of());
        return serviceNamesCache.get();
      } else {
        throw e;
      }
    }
    if (bytes.length == 0) {
      // Check for empty bytes. Otherwise, a HashSet containing a single empty string is returned.
      serviceNamesCache = Optional.of(ImmutableSet.of());
    } else {
      serviceNamesCache = Optional.of(ImmutableSet.copyOf(
          Splitter.on(SERVICE_NAME_DELIMITER).splitToList(new String(bytes, CHARSET))));

    }
    return serviceNamesCache.get();
  }
}
