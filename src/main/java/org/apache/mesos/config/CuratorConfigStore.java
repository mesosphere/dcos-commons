package org.apache.mesos.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.state.CuratorSchemaVersionStore;
import org.apache.mesos.state.SchemaVersionStore;
import org.apache.mesos.storage.CuratorPersister;
import org.apache.zookeeper.KeeperException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CuratorConfigStore stores String Configurations in Zookeeper.
 *
 * The ZNode structure in Zookeeper is as follows:
 * rootPath
 *     -> ConfigTarget (contains UUID)
 *     -> Configurations/
 *         -> [Config-ID-0] (contains serialized config)
 *         -> [Config-ID-1] (contains serialized config)
 *         -> ...
 *
 * @param <T> The {@code Configuration} object to be serialized and deserialized in the
 *            implementation of this interface
 */
public class CuratorConfigStore<T extends Configuration> implements ConfigStore<T> {

    private static final Logger logger = LoggerFactory.getLogger(CuratorConfigStore.class);

    /**
     * @see CuratorSchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final CuratorPersister curator;
    private final String configurationsPath;
    private final String targetPath;

    /**
     * Creates a new {@link ConfigStore} which uses Curator with a default {@link RetryPolicy}.
     *
     * @param rootPath The path to store data in, eg "/FrameworkName"
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     */
    public CuratorConfigStore(String rootPath, String connectionString) {
        this(rootPath, connectionString, new ExponentialBackoffRetry(
                DEFAULT_CURATOR_POLL_DELAY_MS, DEFAULT_CURATOR_MAX_RETRIES));
    }

    /**
     * Creates a new {@link ConfigStore} which uses Curator with a custom {@link RetryPolicy}.
     *
     * @param rootPath The path to store data in, eg "/FrameworkName"
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     * @param retryPolicy The custom {@link RetryPolicy}
     */
    public CuratorConfigStore(String rootPath, String connectionString, RetryPolicy retryPolicy) {
        if (!rootPath.startsWith("/")) {
            throw new IllegalArgumentException("rootPath must start with '/': " + rootPath);
        }
        this.curator = new CuratorPersister(connectionString, retryPolicy);

        // Check version up-front:
        SchemaVersionStore versionStore = new CuratorSchemaVersionStore(curator, rootPath);
        int currentVersion = versionStore.fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software", currentVersion));
        }

        this.targetPath = rootPath + "/" + TARGET_PATH_NAME;
        this.configurationsPath = rootPath + "/" + CONFIGURATIONS_PATH_NAME;
    }

    @Override
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();
        String path = getConfigPath(id);
        try {
            curator.store(path, config.getBytes());
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to serialize or store configuration to path '%s': %s",
                    path, config), e);
        }

        return id;
    }

    @Override
    public T fetch(UUID id, ConfigurationFactory<T> factory) throws ConfigStoreException {
        String path = getConfigPath(id);
        try {
            return factory.parse(curator.fetch(path));
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to retrieve or deserialize configuration '%s' from path '%s'",
                    id, path), e);
        }
    }

    @Override
    public void clear(UUID id) throws ConfigStoreException {
        String path = getConfigPath(id);
        try {
            curator.clear(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Configuration should not
            // result in an exception.
            logger.warn("Requested configuration '{}' to be deleted does not exist at path '{}'",
                    id, path);
            return;
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to delete configuration '%s' at path '%s'", id, path), e);
        }
    }

    @Override
    public Collection<UUID> list() throws ConfigStoreException {
        try {
            Collection<UUID> ids = new ArrayList<>();
            for (String id : curator.getChildren(configurationsPath)) {
                ids.add(UUID.fromString(id));
            }
            return ids;
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Configuration should not
            // result in an exception.
            logger.warn("Configuration list at path '{}' does not exist: returning empty list",
                    configurationsPath);
            return new ArrayList<>();
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to retrieve list of configurations from '%s'", configurationsPath), e);
        }
    }

    @Override
    public void setTargetConfig(UUID id) throws ConfigStoreException {
        try {
            curator.store(targetPath, id.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to assign current target configuration to '%s' at path '%s'",
                    id, targetPath), e);
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        try {
            return UUID.fromString(new String(curator.fetch(targetPath), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to retrieve current target configuration from path '%s'",
                    targetPath), e);
        }
    }

    private String getConfigPath(UUID id) {
        return configurationsPath + "/" + id.toString();
    }
}
