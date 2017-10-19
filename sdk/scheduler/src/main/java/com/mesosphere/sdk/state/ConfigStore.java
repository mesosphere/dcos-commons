package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * An implementation of {@link ConfigStore} which relies on the provided {@link Persister} for data persistence.
 * <p>
 * <p>The ZNode structure in Zookeeper is as follows:
 * <br>rootPath/
 * <br>&nbsp;-> ConfigTarget (contains UUID)
 * <br>&nbsp;-> Configurations/
 * <br>&nbsp;&nbsp;-> [Config-ID-0] (contains serialized config)
 * <br>&nbsp;&nbsp;-> [Config-ID-1] (contains serialized config)
 * <br>&nbsp;&nbsp;-> ...
 *
 * @param <T> The {@code Configuration} object to be serialized and deserialized in the
 *            implementation of this interface
 */
public class ConfigStore<T extends Configuration> implements ConfigTargetStore {

    private static final Logger logger = LoggerFactory.getLogger(ConfigStore.class);

    /**
     * @see SchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final ConfigurationFactory<T> factory;
    private final Persister persister;
    private final Map<UUID, T> cache = new HashMap<>();

    /**
     * Creates a new {@link ConfigStore} which uses the provided {@link Persister} to access configuration data.
     */
    public ConfigStore(ConfigurationFactory<T> factory, Persister persister) {
        this.factory = factory;
        this.persister = persister;

        // Check version up-front:
        int currentVersion = new SchemaVersionStore(persister).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }
    }

    /**
     * Serializes the provided {@link Configuration} using its {@link Configuration#getBytes()}
     * function, writes it to storage, and returns the UUID which it was stored against.
     *
     * @throws ConfigStoreException if serialization or writing fails
     */
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();
        String path = getConfigPath(id);
        byte[] data = config.getBytes();
        try {
            persister.set(path, data);
        } catch (PersisterException e) {
            throw new ConfigStoreException(e, String.format(
                    "Failed to store configuration to path '%s': %s", path, config));
        }

        cache.put(id, config);
        return id;
    }

    /**
     * Retrieves and deserializes the {@link Configuration} assigned to the provided UUID, or throws
     * an exception if no config with the provided UUID was found.
     *
     * @param id The UUID of the configuration to be fetched
     * @return The deserialized configuration
     * @throws ConfigStoreException if retrieval or deserialization fails, or if the requested
     *                              config is missing
     */
    public T fetch(UUID id) throws ConfigStoreException {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        String path = getConfigPath(id);
        logger.info("Fetching configuration with ID={} from {}", id, path);
        byte[] data;
        try {
            data = persister.get(path);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                throw new ConfigStoreException(Reason.NOT_FOUND, String.format(
                        "Configuration '%s' was not found at path '%s'", id, path), e);
            } else {
                throw new ConfigStoreException(e, String.format(
                        "Failed to retrieve configuration '%s' from path '%s'", id, path));
            }
        }

        T config = factory.parse(data);
        cache.put(id, config);
        return config;
    }

    /**
     * Deletes the configuration with the provided UUID, or does nothing if no matching
     * configuration is found.
     *
     * @param id The UUID of the configuration to be deleted
     * @throws ConfigStoreException if the configuration is found but deletion fails
     */
    public void clear(UUID id) throws ConfigStoreException {
        String path = getConfigPath(id);
        try {
            persister.recursiveDelete(path);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Configuration should not result in an exception.
                logger.warn("Requested configuration '{}' to be deleted does not exist at path '{}'", id, path);
                return;
            } else {
                throw new ConfigStoreException(e, String.format(
                        "Failed to delete configuration '%s' at path '%s'", id, path));
            }
        }

        cache.remove(id);
    }

    /**
     * Returns a list of all stored configuration UUIDs, or an empty list if none are found.
     *
     * @throws ConfigStoreException if list retrieval fails
     */
    public Collection<UUID> list() throws ConfigStoreException {
        try {
            Collection<UUID> ids = new ArrayList<>();
            for (String id : persister.getChildren(CONFIGURATIONS_PATH_NAME)) {
                try {
                    ids.add(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                    throw new ConfigStoreException(Reason.SERIALIZATION_ERROR,
                            String.format("Invalid UUID value: %s", id), e);
                }
            }
            return ids;
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Configuration should not result in an exception.
                logger.warn("Configuration list at path '{}' does not exist: returning empty list",
                        CONFIGURATIONS_PATH_NAME);
                return new ArrayList<>();
            } else {
                throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                        "Failed to retrieve list of configurations from '%s'", CONFIGURATIONS_PATH_NAME), e);
            }
        }
    }

    @Override
    public void setTargetConfig(UUID id) throws ConfigStoreException {
        try {
            persister.set(TARGET_PATH_NAME, id.toString().getBytes(StandardCharsets.UTF_8));
        } catch (PersisterException e) {
            throw new ConfigStoreException(e, String.format(
                    "Failed to assign current target configuration to '%s' at path '%s'",
                    id, TARGET_PATH_NAME));
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        String uuidStr;
        try {
            uuidStr = new String(persister.get(TARGET_PATH_NAME), StandardCharsets.UTF_8);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                throw new ConfigStoreException(Reason.NOT_FOUND, String.format(
                        "Current target configuration couldn't be found at path '%s'", TARGET_PATH_NAME), e);
            } else {
                throw new ConfigStoreException(e, String.format(
                        "Failed to retrieve current target configuration from path '%s'", TARGET_PATH_NAME));
            }
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, String.format(
                    "Failed to parse '%s' as a UUID", uuidStr));
        }
    }

    private static String getConfigPath(UUID id) {
        return PersisterUtils.join(CONFIGURATIONS_PATH_NAME, id.toString());
    }
}
