package org.apache.mesos.storage;

import java.util.List;

/**
 * An abstract interface to some persistent store.
 */
public interface StorageAccess {

  /**
   * Populates the entry at {@code path} with the provided {@code data}. The provided {@code path}
   * will be created automatically if it doesn't exist.
   *
   * @throws StorageException in the event of an error with the underlying storage
   */
  public void set(String path, byte[] data) throws StorageException;

  /**
   * Retrieves the content of the file at {@code path}, or throws an exception if it doesn't exist.
   *
   * @throws StorageException in the event of a missing file or an error with the underlying storage
   */
  public byte[] get(String path) throws StorageException;

  /**
   * Deletes the provided {@code path}, and any applicable children. This is a no-op if the path
   * doesn't exist.
   *
   * @throws StorageException in the event of an error with the underlying storage
   */
  public void delete(String path) throws StorageException;

  /**
   * Returs a list of the subnodes in the provided {@code path}. If the path doesn't exist, this
   * returns an empty list.
   * For example, list("/etc") => ["hosts", "groups", "passwd"]
   *
   *
   * @throws StorageException in the event of an error with the underlying storage
   */
  public List<String> list(String path) throws StorageException;

  /**
   * Returns whether the provided {@code path} exists.
   *
   * @throws StorageException in the event of an error with the underlying storage
   */
  public boolean exists(String path) throws StorageException;

}
