package com.mesosphere.sdk.storage;

import java.util.Collection;
import java.util.Map;

/**
 * A low-level interface for key/value storage in a tree structure.
 *
 * <p>Individual nodes may be the parent of other nodes. Some of these parent nodes may lack any data of their own. The
 * root-level node (with path "", or "/") is considered to always be present.
 *
 * <p>This interface should be implemented in order to store and fetch data, with paths delimited by
 * {@link com.mesosphere.sdk.storage.PersisterUtils#PATH_DELIM}.
 */
public interface Persister {

    /**
     * Retrieves the previously stored data at the specified path, or throws an exception if the path doesn't exist.
     * If the path is valid but has no data (i.e. is a parent of another path), this returns {@code null}.
     *
     * @throws PersisterException if the requested path doesn't exist, or for other access errors
     */
    byte[] get(String path) throws PersisterException;

    /**
     * Returns the names of child nodes at the provided path. Some returned nodes may have {@code null} data when
     * retrieved via {@link #get(String)}, indicating that these are stub parent entries to other nodes.
     *
     * <p>To translate the returned values to full absolute paths, they may be joined with the provided {@code path}
     * input using {@link PersisterUtils#join(String, String)}.
     *
     * <p>To get the list of nodes at the root, use "" or "/" as the input path.
     *
     * @throws PersisterException if the requested path doesn't exist, or for other access errors
     */
    Collection<String> getChildren(String path) throws PersisterException;

    /**
     * Writes a single value to storage at the specified path, replacing any existing data at the path or creating the
     * path if it doesn't exist yet.
     *
     * @throws PersisterException in the event of an access error
     */
    void set(String path, byte[] bytes) throws PersisterException;

    /**
     * Atomically writes many values to storage at once.
     *
     * @see #set(String, byte[])
     * @throws PersisterException in the event of an access error, in which case no changes should have been made
     */
    void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException;

    /**
     * Recursively deletes the data at the specified path, or throws an exception if no data existed at that location.
     *
     * <p>Deleting the root node (as "" or "/") will result in all nodes EXCEPT the root node being deleted.
     *
     * @throws PersisterException if the data at the requested path didn't exist, or for other access errors
     */
    void deleteAll(String path) throws PersisterException;

    /**
     * Closes this storage and cleans up any local client resources. No other operations should be performed against the
     * instance after calling this.
     */
    void close();
}
