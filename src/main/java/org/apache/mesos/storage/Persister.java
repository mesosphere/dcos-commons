package org.apache.mesos.storage;

import java.util.Collection;

/**
 * This interface should be implemented in order to store and fetch data.
 */
public interface Persister {
    void store(String path, byte[] bytes) throws Exception;
    byte[] fetch(String path) throws Exception;
    void clear(String path) throws Exception;
    Collection<String> getChildren(String path) throws Exception;
}
