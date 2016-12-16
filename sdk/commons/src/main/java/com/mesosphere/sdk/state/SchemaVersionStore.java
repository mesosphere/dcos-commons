package com.mesosphere.sdk.state;

/**
 * Used by StateStore and ConfigStore implementations to retrieve and validate the Schema Version
 * against whatever version is supported by those respective stores. The Schema Version is a single
 * integer common across all persisted stores.
 *
 * Both of these services share the same schema version, which is a number that monotonically
 * increases. Each implementation is responsible for handling its migration between schema versions.
 */
public interface SchemaVersionStore {

    /**
     * Retrieves the current schema version. This should be called by the StateStore or ConfigStore
     * implementation before reading any other data. If the returned value is unsupported, the
     * StateStore or ConfigStore is responsible for migrating the data to a version that's
     * compatible. Note that this may auto-populate the underlying schema version if the value isn't
     * currently present.
     *
     * @return the current schema version, which may be auto-populated with the current version
     * @throws StateStoreException if retrieving the schema version fails
     */
    public int fetch() throws StateStoreException;

    /**
     * Updates the schema version to the provided value. This should only be called as part of a
     * migration to a new schema.
     *
     * @param version the new schema version to store
     * @throws StateStoreException if storing the schema version fails
     */
    public void store(int version) throws StateStoreException;

    /**
     * Convenience method for checking whether the current schema version falls within a supported
     * range. If this returns false, the implementer is expected to perform a migration to the
     * current version, or to raise an exception.
     */
    public static boolean isSupported(int current, int min, int max) {
        return current >= min && current <= max;
    }

}
