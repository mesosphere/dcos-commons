package org.apache.mesos.config;

import java.util.Optional;

/**
 * This interface defines requirements for objects which wish to be stored in the ConfigStore.
 * @param <K> The key type
 * @param <V> The value type
 */
public interface Configuration<K, V> {
    /**
     * Returns a byte representation of this Configuration which is suitable for writing to disk.
     */
    byte[] getBytes() throws ConfigStoreException;

    /**
     * Returns a JSON representation of this Configuration which is suitable for displaying to the
     * user.
     */
    String toJsonString() throws Exception;

    /**
     * Provides a method for retrieving the values associated with keys.
     * @param key A key associated with a value.
     * @return A value associated with the provided key.
     */
    Optional<V> get(K key);
}
