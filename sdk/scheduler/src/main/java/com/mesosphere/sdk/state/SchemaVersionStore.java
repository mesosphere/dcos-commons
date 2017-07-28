package com.mesosphere.sdk.state;

import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by StateStore and ConfigStore implementations to retrieve and validate the Schema Version against whatever
 * version is supported by those respective stores. The Schema Version is a single integer common across all persisted
 * stores.
 *
 * Both of these services share the same schema version, which is a number that monotonically increases. Each
 * implementation is responsible for handling its migration between schema versions.
 */
public class SchemaVersionStore {

    /**
     * This must never change, as it affects the serialization of the SchemaVersion node.
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final Logger logger = LoggerFactory.getLogger(SchemaVersionStore.class);

    /**
     * Increment this whenever CuratorStateStore or CuratorConfigStore change in a way that
     * requires explicit migration.
     *
     * The migration implementation itself is not yet defined (let's wait until we need to actually
     * do it..)
     *
     * @see ConfigStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see ConfigStore#MAX_SUPPORTED_SCHEMA_VERSION
     * @see StateStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see StateStore#MAX_SUPPORTED_SCHEMA_VERSION
     */
    private static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * This name/path must remain the same forever. It's the basis of all other migrations.
     * If it's not present, it's automatically created and initialized to the value of
     * {@code CURRENT_SCHEMA_VERSION}, on the assumption that the framework is being launched for
     * the first time.
     */
    private static final String SCHEMA_VERSION_NAME = "SchemaVersion";

    private final Persister persister;

    /**
     * Creates a new version store against the provided Framework Name, as would be provided to
     * {@link ConfigStore} or {@link StateStore}.
     */
    SchemaVersionStore(Persister persister) {
        this.persister = persister;
    }

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
    public int fetch() throws StateStoreException {
        try {
            logger.debug("Fetching schema version from '{}'", SCHEMA_VERSION_NAME);
            byte[] bytes = persister.get(SCHEMA_VERSION_NAME);
            if (bytes.length == 0) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Invalid data when fetching schema version in '%s'", SCHEMA_VERSION_NAME));
            }
            String rawString = new String(bytes, CHARSET);
            logger.debug("Schema version retrieved from '{}': {}", SCHEMA_VERSION_NAME, rawString);
            try {
                return Integer.parseInt(rawString);
            } catch (NumberFormatException e) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Unable to parse fetched schema version: '%s' from path: %s",
                        rawString, SCHEMA_VERSION_NAME), e);
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // The schema version doesn't exist yet. Initialize to the current version.
                logger.debug("Schema version not found at path: {}. New service install? " +
                        "Initializing path to schema version: {}.",
                        SCHEMA_VERSION_NAME, CURRENT_SCHEMA_VERSION);
                store(CURRENT_SCHEMA_VERSION);
                return CURRENT_SCHEMA_VERSION;
            } else {
                throw new StateStoreException(Reason.STORAGE_ERROR, "Storage error when fetching schema version", e);
            }
        }
    }

    /**
     * Updates the schema version to the provided value. This should only be called as part of a
     * migration to a new schema.
     *
     * @param version the new schema version to store
     * @throws StateStoreException if storing the schema version fails
     */
    public void store(int version) throws StateStoreException {
        try {
            String versionStr = String.valueOf(version);
            logger.debug("Storing schema version: '{}' into path: {}",
                    versionStr, SCHEMA_VERSION_NAME);
            persister.set(SCHEMA_VERSION_NAME, versionStr.getBytes(CHARSET));
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, String.format(
                    "Storage error when storing schema version %d", version), e);
        }
    }

    /**
     * Convenience method for checking whether the current schema version falls within a supported
     * range. If this returns false, the implementer is expected to perform a migration to the
     * current version, or to raise an exception.
     */
    public static boolean isSupported(int current, int min, int max) {
        return current >= min && current <= max;
    }
}
