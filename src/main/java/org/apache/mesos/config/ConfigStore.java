package org.apache.mesos.config;

import java.util.Collection;
import java.util.UUID;

/**
 * This interface should be implemented in order to store and fetch Configurations
 * presented to Schedulers.
 *
 * In general a Configuration should describe the desired state of a Framework.
 * They are reference by their IDs.  It can be determined whether a particular Task
 * is up to date with a particular Configuration by reference to a Task label
 * indicating its Configuration ID.
 */
public interface ConfigStore<T> {
    UUID store(T config) throws ConfigStoreException;
    T fetch(UUID id) throws ConfigStoreException;
    void clear(UUID id) throws ConfigStoreException;
    Collection<UUID> list() throws ConfigStoreException;

    void setTargetConfig(UUID id) throws ConfigStoreException;
    UUID getTargetConfig() throws ConfigStoreException;
}
