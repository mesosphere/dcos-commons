package com.mesosphere.sdk.storage;

import java.util.Collection;
import java.util.Map;

/**
 * A low-level interface for key/value storage.
 *
 * This interface should be implemented in order to store and fetch data, with paths delimited by
 * {@link com.mesosphere.sdk.state.PathUtils#PATH_DELIM}.
 */
public interface Persister {

    /**
     * Retrieves the previously stored data at the specified path, or throws an exception if the
     * data is missing.
     *
     * @throws PersisterException if the data at the requested path doesn't exist, or for other access errors
     */
    byte[] get(String path) throws PersisterException;

    /**
     * Returns the names of child nodes at the provided path.
     *
     * To get the list of nodes at the root, use a path of "/": {@link com.mesosphere.sdk.state.PathUtils#PATH_DELIM}.
     *
     * The returned values may be joined with the provided {@code path} to get their full paths. Paths are delimited by
     * {@link com.mesosphere.sdk.state.PathUtils#PATH_DELIM}.
     */
    Collection<String> getChildren(String path) throws PersisterException;

    /**
     * Writes a single value to storage at the specified path, replacing existing data at the path
     * if any.
     */
    void set(String path, byte[] bytes) throws PersisterException;

    /**
     * Atomically writes many values to storage at once.
     */
    void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException;

    /**
     * Recursively deletes the data at the specified path, or throws an exception if no data existed at that location.
     *
     * @throws PersisterException if the data at the requested path didn't exist, or for other access errors
     */
    void delete(String path) throws PersisterException;

    /**
     * Closes this storage and cleans up any resources. No other operations should be performed
     * against the instance after calling this.
     */
    void close();
}
