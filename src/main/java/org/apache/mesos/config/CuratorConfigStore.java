package org.apache.mesos.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
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
 *         -> Config-ID-0 (contains serialized config)
 *         -> Config-ID-1 (contains serialized config)
 *         -> ...
 *
 * @param <T> The {@code Configuration} object to be serialized and deserialized in the
 *            implementation of this interface
 */
public class CuratorConfigStore<T extends Configuration>
        extends CuratorPersister implements ConfigStore<T> {
    private static final Logger logger = LoggerFactory.getLogger(CuratorConfigStore.class);

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

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
        super(connectionString, retryPolicy);
        if (!rootPath.startsWith("/")) {
            throw new IllegalArgumentException("rootPath must start with '/': " + rootPath);
        }
        this.targetPath = rootPath + "/" + TARGET_PATH_NAME;
        this.configurationsPath = rootPath + "/" + CONFIGURATIONS_PATH_NAME;
    }

    @Override
    public UUID store(T config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();

        try {
            store(getConfigPath(id) , config.getBytes());
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }

        return id;
    }

    @Override
    public T fetch(UUID id, ConfigurationFactory<T> factory) throws ConfigStoreException {
        try {
            return factory.parse(fetch(getConfigPath(id)));
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }

    @Override
    public void clear(UUID id) throws ConfigStoreException {
        try {
            clear(getConfigPath(id));
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Configuration should not
            // result in an exception.
            logger.warn("Clearing unset Configuration ID: " + id);
            return;
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }

    @Override
    public Collection<UUID> list() throws ConfigStoreException {
        try {
            Collection<UUID> ids = new ArrayList<>();
            for (String id : getChildren(configurationsPath)) {
                ids.add(UUID.fromString(id));
            }
            return ids;
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }

    @Override
    public void setTargetConfig(UUID id) throws ConfigStoreException {
        try {
            store(targetPath, id.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }

    @Override
    public UUID getTargetConfig() throws ConfigStoreException {
        try {
            return UUID.fromString(new String(fetch(targetPath), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }

    private String getConfigPath(UUID id) {
        return configurationsPath + "/" + id.toString();
    }
}
