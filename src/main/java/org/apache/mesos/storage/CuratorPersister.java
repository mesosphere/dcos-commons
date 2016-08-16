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

    public CuratorPersister(String connectionString, RetryPolicy retryPolicy) {
        this.connectionString = connectionString;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public void store(String path, byte[] bytes) throws Exception {
        CuratorFramework client = startClient();
        try {
            client.create().creatingParentsIfNeeded().forPath(path, bytes);
        } catch (KeeperException.NodeExistsException e) {
            client.setData().forPath(path, bytes);
        } finally {
            client.close();
        }
    }

    @Override
    public byte[] fetch(String path) throws Exception {
        CuratorFramework client = startClient();
        try {
            return client.getData().forPath(path);
        } finally {
            client.close();
        }
    }

    @Override
    public void clear(String path) throws Exception {
        CuratorFramework client = startClient();
        try {
            client.delete().deletingChildrenIfNeeded().forPath(path);
        } finally {
            client.close();
        }
    }

    @Override
    public Collection<String> getChildren(String path) throws Exception {
        CuratorFramework client = startClient();
        try {
            return client.getChildren().forPath(path);
        } finally {
            client.close();
        }
    }

    private CuratorFramework startClient() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
        client.start();
        return client;
    }
}
