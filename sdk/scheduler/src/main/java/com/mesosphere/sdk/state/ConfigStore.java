package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * An implementation of {@link ConfigStore} which relies on the provided {@link Persister} for data persistence.
 * <p>
 * <p>The ZNode structure in Zookeeper is as follows:
 * <br>namespacedPath/ ("Services/NAMESPACE/" or "/")
 * <br>&nbsp; ConfigTarget (contains UUID)
 * <br>&nbsp; Configurations/
 * <br>&nbsp; &nbsp; UUID-0 (contains serialized config)
 * <br>&nbsp; &nbsp; UUID-1 (contains serialized config)
 *
 * @param <T> The {@code Configuration} object to be serialized and deserialized in the
 *            implementation of this interface
 */
public class ConfigStore<T extends Configuration> implements ConfigTargetStore {

    private static final Logger logger = LoggingUtils.getLogger(ConfigStore.class);

    private static final String TARGET_ID_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final Persister persister;
    private final String namespace;
    private final Map<UUID, T> cache = new HashMap<>();

    private ConfigurationFactory<T> factory;

    /**
     * Creates a new {@link ConfigStore} which uses the provided {@link Persister} to access configuration data within
     * the root namespace.
     *
     * @param factory The factory used to convert raw bytes to config objects of type {@code T}
     * @param persister The persister which holds the config data
     */
    public ConfigStore(ConfigurationFactory<T> factory, Persister persister) {
        this(factory, persister, "");
    }

    /**
     * Creates a new {@link ConfigStore} which uses the provided {@link Persister} to access configuration data within
     * the provided {@code namespace}.
     *
     * @param factory The factory used to convert raw bytes to config objects of type {@code T}
     * @param persister The persister which holds the config data
     * @param namespace The namespace for data to be stored within, or an empty string for no namespacing
     */
    public ConfigStore(ConfigurationFactory<T> factory, Persister persister, String namespace) {
        this.factory = factory;
        this.persister = persister;
        this.namespace = namespace;
    }

    /**
     * Overrides the configuration factory which was provided in the constructor.
     */
    public void setConfigurationFactory(ConfigurationFactory<T> factory) {
        this.factory = factory;
    }

    /**
     * Indicates whether the provided key is present in the store.
     */
    public boolean hasKey(UUID id) throws ConfigStoreException {
        return cache.containsKey(id) || list().contains(id);
    }

    /**
     * Serializes the provided {@link Configuration} using its {@link Configuration#getBytes()}
     * function, writes it to storage, and returns the UUID which it was stored against.
     *
     * @throws ConfigStoreException if serialization or writing fails
     */
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();
        store(id, config);
        return id;
    }

    /**
     * Serializes the provided {@link Configuration} using its {@link Configuration#getBytes()}
     * function, writes it to storage with the provided ID as a key.
     *
     * @throws ConfigStoreException is serialization or writing fails
     */
    public void store(UUID id, T config) throws ConfigStoreException {
        String path = getConfigPath(namespace, id);
        byte[] data = config.getBytes();
        try {
            persister.set(path, data);
        } catch (PersisterException e) {
            throw new ConfigStoreException(e, String.format(
                    "Failed to store configuration to path '%s': %s", path, config));
        }

        cache.put(id, config);
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

        String path = getConfigPath(namespace, id);
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
        String path = getConfigPath(namespace, id);
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
        String configurationsPath = getConfigsPath(namespace);
        try {
            Collection<UUID> ids = new ArrayList<>();
            for (String id : persister.getChildren(configurationsPath)) {
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
                        configurationsPath);
                return new ArrayList<>();
            } else {
                throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                        "Failed to retrieve list of configurations from '%s'", configurationsPath), e);
            }
        }
    }

    @Override
    public void setTargetConfig(UUID id) throws ConfigStoreException {
        String targetIdPath = getTargetIdPath(namespace);
        try {
            persister.set(targetIdPath, id.toString().getBytes(StandardCharsets.UTF_8));
        } catch (PersisterException e) {
            throw new ConfigStoreException(e, String.format(
                    "Failed to assign current target configuration to '%s' at path '%s'", id, targetIdPath));
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        String targetIdPath = getTargetIdPath(namespace);
        String uuidStr;
        try {
            uuidStr = new String(persister.get(targetIdPath), StandardCharsets.UTF_8);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                throw new ConfigStoreException(Reason.NOT_FOUND, String.format(
                        "Current target configuration couldn't be found at path '%s'", targetIdPath), e);
            } else {
                throw new ConfigStoreException(e, String.format(
                        "Failed to retrieve current target configuration from path '%s'", targetIdPath));
            }
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, String.format(
                    "Failed to parse '%s' as a UUID", uuidStr));
        }
    }

    /**
     * @return {@code Services/[namespace]/ConfigTarget}, or {@code ConfigTarget}
     */
    private static String getTargetIdPath(String namespace) {
        return PersisterUtils.getServiceNamespacedRootPath(namespace, TARGET_ID_PATH_NAME);
    }

    /**
     * @return {@code Services/[namespace]/Configurations/[id]}, or {@code Configurations/[id]}
     */
    private static String getConfigPath(String namespace, UUID id) {
        return PersisterUtils.join(getConfigsPath(namespace), id.toString());
    }

    /**
     * @return {@code Services/[namespace]/Configurations}, or {@code Configurations}
     */
    private static String getConfigsPath(String namespace) {
        return PersisterUtils.getServiceNamespacedRootPath(namespace, CONFIGURATIONS_PATH_NAME);
    }
}
