package org.apache.mesos.storage;

import java.util.Collection;
import java.util.Map;

/**
 * This interface should be implemented in order to store and fetch data.
 */
public interface Persister {
    /**
     * Atomically writes many values to storage at once.
     */
    void storeMany(Map<String, byte[]> pathBytesMap) throws Exception;

    /**
     * Writes a single value to storage at the specified path.
     */
    void store(String path, byte[] bytes) throws Exception;

    /**
     * Retrieves the previously stored data at the specified path, or throws an exception if the
     * data is missing.
     */
    byte[] fetch(String path) throws Exception;

    /**
     * Deletes the data at the specified path, or throws an exception if the data didn't already
     * exist.
     */
    void clear(String path) throws Exception;

    /**
     * Returns the names child nodes at the provided path. The returned values may be joined with
     * the provided {@code path} to get their full paths.
     */
    Collection<String> getChildren(String path) throws Exception;

    /**
     * Closes this storage and cleans up any resources. No other operations should be performed
     * against this instance after calling close().
     */
    void close();
}
