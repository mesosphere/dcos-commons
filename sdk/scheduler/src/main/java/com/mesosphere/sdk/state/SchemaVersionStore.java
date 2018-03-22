package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
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
    public SchemaVersionStore(Persister persister) {
        this.persister = persister;
    }

    /**
     * Retrieves the current schema version. This should be called by the StateStore or ConfigStore
     * implementation before reading any other data. If the returned value is unsupported, the
     * StateStore or ConfigStore is responsible for migrating the data to a version that's
     * compatible. Note that this may auto-populate the underlying schema version if the value isn't
     * currently present.
     *
     * @param expectedVersion the expected schema version to be stored if no version is currently set or to be checked
     *                        for equality if a version is currently set
     * @throws StateStoreException if retrieving the schema version fails
     * @throws IllegalStateException if a value is present which doesn't match the expected value
     */
    public void check(int expectedVersion) throws StateStoreException {
        try {
            logger.debug("Fetching schema version from '{}'", SCHEMA_VERSION_NAME);
            byte[] bytes = persister.get(SCHEMA_VERSION_NAME);
            if (bytes.length == 0) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Invalid data when fetching schema version in '%s'", SCHEMA_VERSION_NAME));
            }
            String rawString = new String(bytes, CHARSET);
            logger.debug("Schema version retrieved from '{}': {}", SCHEMA_VERSION_NAME, rawString);
            int currentVersion;
            try {
                currentVersion = Integer.parseInt(rawString);
            } catch (NumberFormatException e) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Unable to parse fetched schema version: '%s' from path: %s",
                        rawString, SCHEMA_VERSION_NAME), e);
            }
            if (!SchemaVersionStore.isSupported(currentVersion, expectedVersion, expectedVersion)) {
                throw new IllegalStateException(String.format(
                        "Storage schema version %d is not supported by this software (expected: %d)",
                        currentVersion, expectedVersion));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // The schema version doesn't exist yet. Initialize to the current version.
                logger.debug("Schema version not found at path: {}. New service install? " +
                        "Initializing path to schema version: {}.",
                        SCHEMA_VERSION_NAME, expectedVersion);
                store(expectedVersion);
            } else {
                throw new StateStoreException(
                        Reason.STORAGE_ERROR, "Storage error when fetching schema storage", e);
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
    @VisibleForTesting
    void store(int version) throws StateStoreException {
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
