package org.apache.mesos.storage;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.zookeeper.KeeperException;

import java.util.Collection;

/**
 * The CuratorPersistor implemenation of the Persister interface
 * provides for persistence and retrieval of data from Zookeeper.
 */
public class CuratorPersister implements Persister {
    private final String connectionString;
    private final RetryPolicy retryPolicy;
    private final CuratorFramework client;

    public CuratorPersister(String connectionString, RetryPolicy retryPolicy) {
        this.connectionString = connectionString;
        this.retryPolicy = retryPolicy;
        this.client = startClient();
    }

    @Override
    public void store(String path, byte[] bytes) throws Exception {
        try {
            client.create().creatingParentsIfNeeded().forPath(path, bytes);
        } catch (KeeperException.NodeExistsException e) {
            client.setData().forPath(path, bytes);
        }
    }

    @Override
    public byte[] fetch(String path) throws Exception {
        return client.getData().forPath(path);
    }

    @Override
    public void clear(String path) throws Exception {
        client.delete().deletingChildrenIfNeeded().forPath(path);
    }

    @Override
    public Collection<String> getChildren(String path) throws Exception {
        return client.getChildren().forPath(path);
    }

    @Override
    public void close() {
        client.close();
    }

    private CuratorFramework startClient() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
        client.start();
        return client;
    }
}
