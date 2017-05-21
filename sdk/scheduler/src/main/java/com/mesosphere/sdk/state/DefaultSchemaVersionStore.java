package com.mesosphere.sdk.state;

import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link SchemaVersionStore} which relies on the provided {@link Persister} for data persistence.
 */
public class DefaultSchemaVersionStore implements SchemaVersionStore {

    /**
     * This must never change, as it affects the serialization of the SchemaVersion node.
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final Logger logger = LoggerFactory.getLogger(DefaultSchemaVersionStore.class);

    /**
     * Increment this whenever CuratorStateStore or CuratorConfigStore change in a way that
     * requires explicit migration.
     *
     * The migration implementation itself is not yet defined (let's wait until we need to actually
     * do it..)
     *
     * @see DefaultConfigStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see DefaultConfigStore#MAX_SUPPORTED_SCHEMA_VERSION
     * @see DefaultStateStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see DefaultStateStore#MAX_SUPPORTED_SCHEMA_VERSION
     */
    static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * This name/path must remain the same forever. It's the basis of all other migrations.
     * If it's not present, it's automatically created and initialized to the value of
     * {@code CURRENT_SCHEMA_VERSION}, on the assumption that the framework is being launched for
     * the first time.
     */
    static final String SCHEMA_VERSION_NAME = "SchemaVersion";

    private final Persister persister;
    private final String schemaVersionPath;

    /**
     * Creates a new version store against the provided Framework Name, as would be provided to
     * {@link DefaultConfigStore} or {@link DefaultStateStore}.
     */
    DefaultSchemaVersionStore(Persister persister) {
        this.persister = persister;
        this.schemaVersionPath = SCHEMA_VERSION_NAME;
    }

    public int fetch() throws StateStoreException {
        try {
            logger.debug("Fetching schema version from '{}'", schemaVersionPath);
            byte[] bytes = persister.get(schemaVersionPath);
            if (bytes.length == 0) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Invalid data when fetching schema version in '%s'", schemaVersionPath));
            }
            String rawString = new String(bytes, CHARSET);
            logger.debug("Schema version retrieved from '{}': {}", schemaVersionPath, rawString);
            try {
                return Integer.parseInt(rawString);
            } catch (NumberFormatException e) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Unable to parse fetched schema version: '%s' from path: %s",
                        rawString, schemaVersionPath), e);
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // The schema version doesn't exist yet. Initialize to the current version.
                logger.debug("Schema version not found at path: {}. New service install? " +
                        "Initializing path to schema version: {}.",
                        schemaVersionPath, CURRENT_SCHEMA_VERSION);
                store(CURRENT_SCHEMA_VERSION);
                return CURRENT_SCHEMA_VERSION;
            } else {
                throw new StateStoreException(Reason.STORAGE_ERROR, "Storage error when fetching schema version", e);
            }
        }
    }

    public void store(int version) throws StateStoreException {
        try {
            String versionStr = String.valueOf(version);
            logger.debug("Storing schema version: '{}' into path: {}",
                    versionStr, schemaVersionPath);
            persister.set(schemaVersionPath, versionStr.getBytes(CHARSET));
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, String.format(
                    "Storage error when storing schema version %d", version), e);
        }
    }
}
