package com.mesosphere.sdk.curator;

import org.apache.curator.RetryPolicy;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.state.SchemaVersionStore;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

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

    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final ConfigurationFactory<T> factory;
    private final CuratorPersister curator;
    private final String configurationsPath;
    private final String targetPath;

    /**
     * Creates a new {@link ConfigStore} which uses Curator with a default {@link RetryPolicy} and
     * connection string.
     *
     * @param frameworkName The name of the framework
     */
    public CuratorConfigStore(ConfigurationFactory<T> factory, String frameworkName) {
        this(factory, frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates a new {@link ConfigStore} which uses Curator with a default {@link RetryPolicy}.
     *
     * @param frameworkName The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     */
    public CuratorConfigStore(ConfigurationFactory<T> factory, String frameworkName, String connectionString) {
        this(factory, frameworkName, connectionString, CuratorUtils.getDefaultRetry());
    }

    /**
     * Creates a new {@link ConfigStore} which uses Curator with a custom {@link RetryPolicy}.
     *
     * @param frameworkName The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     * @param retryPolicy The custom {@link RetryPolicy}
     */
    public CuratorConfigStore(
            ConfigurationFactory<T> factory,
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy) {
        this.factory = factory;
        this.curator = new CuratorPersister(connectionString, retryPolicy);

        // Check version up-front:
        int currentVersion = new CuratorSchemaVersionStore(curator, frameworkName).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }

        final String rootPath = CuratorUtils.toServiceRootPath(frameworkName);
        this.targetPath = CuratorUtils.join(rootPath, TARGET_PATH_NAME);
        this.configurationsPath = CuratorUtils.join(rootPath, CONFIGURATIONS_PATH_NAME);
    }

    @Override
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();
        String path = getConfigPath(id);
        try {
            curator.set(path, config.getBytes());
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to serialize or store configuration to path '%s': %s",
                    path, config), e);
        }

        return id;
    }

    @Override
    public T fetch(UUID id) throws ConfigStoreException {
        String path = getConfigPath(id);
        try {
            return factory.parse(curator.get(path));
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
            curator.delete(path);
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
            curator.set(targetPath, CuratorUtils.serialize(id));
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to assign current target configuration to '%s' at path '%s'",
                    id, targetPath), e);
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        try {
            return UUID.fromString(CuratorUtils.deserialize(curator.get(targetPath)));
        } catch (Exception e) {
            throw new ConfigStoreException(String.format(
                    "Failed to retrieve current target configuration from path '%s'",
                    targetPath), e);
        }
    }

    public void close() {
        curator.close();
    }

    private String getConfigPath(UUID id) {
        return CuratorUtils.join(configurationsPath, id.toString());
    }
}
