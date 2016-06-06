package org.apache.mesos.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.curator.RetryPolicy;
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
 *     -> Configurations
 *         -> Config-ID-0 (contains serialized config)
 *         -> Config-ID-1 (contains serialized config)
 *         -> ...
 */
public class CuratorConfigStore extends CuratorPersister implements ConfigStore {
    private static final String TARGET_PATH_NAME = "ConfigTarget";
    private static final String CONFIGURATIONS_PATH_NAME = "Configurations";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String configurationsPath;
    private String targetPath;

    public CuratorConfigStore(String rootPath, String connectionString, RetryPolicy retryPolicy) {
        super(connectionString, retryPolicy);
        this.targetPath = rootPath + "/" + TARGET_PATH_NAME;
        this.configurationsPath = rootPath + "/" + CONFIGURATIONS_PATH_NAME;
    }

    @Override
    public UUID store(String config) throws ConfigStoreException {
        UUID id = UUID.randomUUID();

        try {
            store(getConfigPath(id) , config.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }

        return id;
    }

    @Override
    public String fetch(UUID id) throws ConfigStoreException {
        try {
            return new String(fetch(getConfigPath(id)), StandardCharsets.UTF_8);
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
