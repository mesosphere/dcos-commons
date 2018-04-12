package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;

/**
 * Used to retrieve and validate the Schema Version against whatever version is supported by the scheduler.
 *
 * The schema version is a number that may change over time as incompatible changes are made to persistent storage.
 * Storage implementations are responsible for handling its migration between schema versions.
 */
public class SchemaVersionStore {

    /**
     * This must never change, as it affects the serialization of the SchemaVersion node.
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final Logger LOGGER = LoggingUtils.getLogger(SchemaVersionStore.class);

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
     * Checks if the current stored version matches the {@code expectedVersion}. If no schema version is present, then
     * the {@code expectedVersion} is written to the store automatically for future checks.
     *
     * @param expectedVersion the expected schema version to be stored if no version is currently set or to be checked
     *                        for equality if a version is currently set
     * @throws StateStoreException if retrieving the schema version fails
     * @throws IllegalStateException if a value is present which doesn't match the expected value
     */
    public void check(int expectedVersion) throws StateStoreException {
        try {
            LOGGER.debug("Fetching schema version from '{}'", SCHEMA_VERSION_NAME);
            byte[] bytes = persister.get(SCHEMA_VERSION_NAME);
            if (bytes.length == 0) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Invalid data when fetching schema version in '%s'", SCHEMA_VERSION_NAME));
            }
            String rawString = new String(bytes, CHARSET);
            LOGGER.debug("Schema version retrieved from '{}': {}", SCHEMA_VERSION_NAME, rawString);
            int currentVersion;
            try {
                currentVersion = Integer.parseInt(rawString);
            } catch (NumberFormatException e) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Unable to parse fetched schema version: '%s' from path: %s",
                        rawString, SCHEMA_VERSION_NAME), e);
            }
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException(String.format(
                        "Storage schema version %d is not supported by this software (expected: %d)",
                        currentVersion, expectedVersion));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // The schema version doesn't exist yet. Initialize to the current version.
                LOGGER.debug("Schema version not found at path: {}. New service install? " +
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
     * Sets the schema version to the provided value. In practice this should only be called if no existing schema
     * version is present, or if a migration to a new schema version just finished.
     *
     * @param version the new schema version to store
     * @throws StateStoreException if storing the schema version fails
     */
    @VisibleForTesting
    void store(int version) throws StateStoreException {
        try {
            String versionStr = String.valueOf(version);
            LOGGER.debug("Storing schema version: '{}' into path: {}",
                    versionStr, SCHEMA_VERSION_NAME);
            persister.set(SCHEMA_VERSION_NAME, versionStr.getBytes(CHARSET));
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, String.format(
                    "Storage error when storing schema version %d", version), e);
        }
    }
}
