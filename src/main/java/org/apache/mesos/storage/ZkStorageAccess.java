package org.apache.mesos.storage;

import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * Implementation of {@link StorageAccess} which stores data within a Zookeeper instance.
 */
public class ZkStorageAccess implements StorageAccess {

  private static final int POLL_DELAY_MS = 1000;
  private static final int CURATOR_MAX_RETRIES = 3;

  private final String rootPath;
  private final CuratorFramework client;

  /**
   * Creates a new Zookeeper storage instance against the provided {@code zkAddress}, with all paths
   * placed within the provided {@code rootPath}.
   *
   * @param zkHostPort One or more hosts/ports to connect to, eg "leader.mesos:1234"
   * @param rootPath Path for data to be stored within, eg "/frameworkName"
   */
  public ZkStorageAccess(String zkHostPort, String rootPath) {
    this.rootPath = rootPath;
    client = CuratorFrameworkFactory.newClient(
        zkHostPort, new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES));
    client.start();
  }

  @Override
  public void set(String path, byte[] data) throws StorageException {
    path = withRootPrefix(path);
    try {
      if (client.checkExists().forPath(path) == null) {
        client.create().creatingParentsIfNeeded().forPath(path, data);
      } else {
        client.setData().forPath(path, data);
      }
    } catch (Exception e) {
      throw new StorageException(
          String.format("Failed to write %d bytes to Zookeeper path %s", data.length, path), e);
    }
  }

  @Override
  public byte[] get(String path) throws StorageException {
    path = withRootPrefix(path);
    try {
      return client.getData().forPath(path);
    } catch (Exception e) {
      throw new StorageException(
          "Failed to read data from Zookeeper path " + path, e);
    }
  }

  @Override
  public void delete(String path) throws StorageException {
    path = withRootPrefix(path);
    try {
      client.delete().deletingChildrenIfNeeded().forPath(path);
    } catch (Exception e) {
      throw new StorageException(
          String.format("Failed to delete path %s from Zookeeper", path), e);
    }
  }

  @Override
  public List<String> list(String path) throws StorageException {
    path = withRootPrefix(path);
    try {
      return client.getChildren().forPath(path);
    } catch (Exception e) {
      throw new StorageException(
          "Failed to retrieve children for Zookeeper path " + path, e);
    }
  }

  @Override
  public boolean exists(String path) throws StorageException {
    path = withRootPrefix(path);
    try {
      return client.checkExists().forPath(path) != null;
    } catch (Exception e) {
      throw new StorageException(
          "Failed to determine existence of Zookeeper path " + path, e);
    }
  }

  private String withRootPrefix(String path) throws StorageException {
    return rootPath + path;
  }
}
