package com.mesosphere.sdk.state;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import jersey.repackaged.com.google.common.base.Joiner;

/**
 * A {@code FrameworkStore} stores the Framework ID for the scheduler. The framework ID is required for the scheduler to
 * re-identify itself with Mesos after initial registration.
 *
 * <p>The structure used in the underlying persister is as follows:
 * <br>rootPath/
 * <br>&nbsp;-> FrameworkID
 */
public class SelectedReservationStore {

    private static final Logger LOGGER = LoggingUtils.getLogger(SelectedReservationStore.class);
    private static final String SELECTED_RESERVATIONS_PATH_NAME = "SelectedReservations";
    // Service names cannot contain double underscores, so we use double underscores as our delimiter:
    private static final String SERVICE_NAME_DELIMITER = "__";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final Persister persister;

    // Local cache:
    private Optional<ImmutableSet<String>> serviceNamesCache;

    /**
     * Creates a new {@link FrameworkStore} which uses the provided {@link Persister} to access the Framework ID.
     *
     * @param persister The persister which holds the data
     */
    public SelectedReservationStore(Persister persister) {
        this.persister = persister;
        this.serviceNamesCache = Optional.empty();
    }

    /**
     * Stores the list of services that are selected for reservations.
     *
     * @param serviceNames The service names to be stored
     * @throws PersisterException when storing the selection fails
     */
    public void storeSelectedReservations(Set<String> serviceNames) throws PersisterException {
        if (serviceNamesCache.isPresent() && serviceNames.equals(serviceNamesCache.get())) {
            // No change.
            return;
        }
        serviceNamesCache = Optional.of(ImmutableSet.copyOf(serviceNames));
        byte[] data = Joiner.on(SERVICE_NAME_DELIMITER).join(serviceNames).getBytes(CHARSET);
        persister.set(SELECTED_RESERVATIONS_PATH_NAME, data);
    }

    /**
     * Removes any previously stored selection or does nothing if nothing was previously stored.
     *
     * @throws PersisterException when clearing the selection fails
     */
    public void clearSelectedReservations() throws PersisterException {
        try {
            serviceNamesCache = Optional.of(ImmutableSet.of());
            persister.recursiveDelete(SELECTED_RESERVATIONS_PATH_NAME);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent value should not result in an exception from us.
                LOGGER.info("Cleared unset value, continuing silently", e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    /**
     * Fetches the previously stored selected reservations, or returns an empty Set if none were previously stored.
     *
     * @return The previously stored selected services, or an empty Set if nothing was listed
     * @throws PersisterException when fetching the data fails
     */
    public ImmutableSet<String> fetchSelectedReservations() throws PersisterException {
        if (serviceNamesCache.isPresent()) {
            return serviceNamesCache.get();
        }

        byte[] bytes;
        try {
            bytes = persister.get(SELECTED_RESERVATIONS_PATH_NAME);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                serviceNamesCache = Optional.of(ImmutableSet.of());
                return serviceNamesCache.get();
            } else {
                throw e;
            }
        }
        ImmutableSet<String> result = ImmutableSet.copyOf(
                Splitter.on(SERVICE_NAME_DELIMITER).splitToList(new String(bytes, CHARSET)));
        serviceNamesCache = Optional.of(result);
        return result;
    }
}
