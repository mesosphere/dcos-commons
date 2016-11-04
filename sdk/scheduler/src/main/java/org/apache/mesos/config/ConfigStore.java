package org.apache.mesos.config;

import java.util.Collection;
import java.util.UUID;

/**
 * This interface should be implemented in order to store and fetch Configurations
 * presented to Schedulers.
 *
 * In general a Configuration should describe the desired state of a Framework.
 * They are referenced by their IDs.  It can be determined whether a particular Task
 * is up to date with a particular Configuration by reference to a Task label
 * indicating its Configuration ID.
 *
 * @param <T> The {@code Configuration} object to be serialized and deserialized in the
 *            implementation of this interface
 */
public interface ConfigStore<T extends Configuration> extends ConfigTargetStore {

    /**
     * Serializes the provided {@link Configuration} using its {@link Configuration#getBytes()}
     * function, writes it to storage, and returns the UUID which it was stored against.
     *
     * @throws ConfigStoreException if serialization or writing fails
     */
    UUID store(T config) throws ConfigStoreException;

    /**
     * Retrieves and deserializes the {@link Configuration} assigned to the provided UUID, or throws
     * an exception if no config with the provided UUID was found.
     *
     * @param id The UUID of the configuration to be fetched
     * @return The deserialized configuration
     * @throws ConfigStoreException if retrieval or deserialization fails, or if the requested
     *                              config is missing
     */
    T fetch(UUID id) throws ConfigStoreException;

    /**
     * Deletes the configuration with the provided UUID, or does nothing if no matching
     * configuration is found.
     *
     * @param id The UUID of the configuration to be deleted
     * @throws ConfigStoreException if the configuration is found but deletion fails
     */
    void clear(UUID id) throws ConfigStoreException;

    /**
     * Returns a list of all stored configuration UUIDs, or an empty list if none are found.
     *
     * @throws ConfigStoreException if list retrieval fails
     */
    Collection<UUID> list() throws ConfigStoreException;
}
