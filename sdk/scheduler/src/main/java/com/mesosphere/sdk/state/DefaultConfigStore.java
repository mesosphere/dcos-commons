package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Stores String Configurations in Zookeeper.
 *
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
public class DefaultConfigStore<T extends Configuration> implements ConfigStore<T> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigStore.class);

    /**
     * @see DefaultSchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final ConfigurationFactory<T> factory;
    private final Persister persister;
    private final String configurationsPath;
    private final String targetPath;

    /**
     * Creates a new {@link ConfigStore} which uses the provided {@link Persister} to access configuration data.
     */
    public DefaultConfigStore(ConfigurationFactory<T> factory, String frameworkName, Persister persister) {
        this.factory = factory;
        this.persister = persister;

        // Check version up-front:
        int currentVersion = new DefaultSchemaVersionStore(persister, frameworkName).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }

        final String rootPath = CuratorUtils.getServiceRootPath(frameworkName);
        this.targetPath = PersisterUtils.join(rootPath, TARGET_PATH_NAME);
        this.configurationsPath = PersisterUtils.join(rootPath, CONFIGURATIONS_PATH_NAME);
    }

    @Override
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();
        String path = getConfigPath(id);
        byte[] data = config.getBytes();
        try {
            persister.set(path, data);
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to store configuration to path '%s': %s", path, config), e);
        }

        return id;
    }

    @Override
    public T fetch(UUID id) throws ConfigStoreException {
        String path = getConfigPath(id);
        byte[] data;
        try {
            data = persister.get(path);
        } catch (KeeperException.NoNodeException e) {
            throw new ConfigStoreException(Reason.NOT_FOUND, String.format(
                    "Configuration '%s' was not found at path '%s'", id, path), e);
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to retrieve configuration '%s' from path '%s'", id, path), e);
        }
        return factory.parse(data);
    }

    @Override
    public void clear(UUID id) throws ConfigStoreException {
        String path = getConfigPath(id);
        try {
            persister.deleteAll(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Configuration should not
            // result in an exception.
            logger.warn("Requested configuration '{}' to be deleted does not exist at path '{}'",
                    id, path);
            return;
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to delete configuration '%s' at path '%s'", id, path), e);
        }
    }

    @Override
    public Collection<UUID> list() throws ConfigStoreException {
        try {
            Collection<UUID> ids = new ArrayList<>();
            for (String id : persister.getChildren(configurationsPath)) {
                ids.add(UUID.fromString(id));
            }
            return ids;
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Configuration should not
            // result in an exception.
            logger.warn("Configuration list at path '{}' does not exist: returning empty list",
                    configurationsPath);
            return new ArrayList<>();
        } catch (IllegalArgumentException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, String.format("Invalid UUID value"), e);
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to retrieve list of configurations from '%s'", configurationsPath), e);
        }
    }

    @Override
    public void setTargetConfig(UUID id) throws ConfigStoreException {
        try {
            persister.set(targetPath, id.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to assign current target configuration to '%s' at path '%s'",
                    id, targetPath), e);
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        String uuidStr;
        try {
            uuidStr = new String(persister.get(targetPath), StandardCharsets.UTF_8);
        } catch (KeeperException.NoNodeException e) {
            throw new ConfigStoreException(Reason.NOT_FOUND, String.format(
                    "Current target configuration couldn't be found at path '%s'",
                    targetPath), e);
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to retrieve current target configuration from path '%s'",
                    targetPath), e);
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, String.format(
                    "Failed to parse '%s' as a UUID", uuidStr));
        }
    }

    public void close() {
        persister.close();
    }

    private String getConfigPath(UUID id) {
        return PersisterUtils.join(configurationsPath, id.toString());
    }
}
