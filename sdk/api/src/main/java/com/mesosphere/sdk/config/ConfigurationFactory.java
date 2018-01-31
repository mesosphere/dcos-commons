package com.mesosphere.sdk.config;

import com.mesosphere.sdk.state.ConfigStoreException;

/**
 * This interface defines a way for previously stored Configuration objects to be reconstructed from
 * a byte array.
 *
 * @param <T> The {@code Configuration} type that will be parsed.
 */
public interface ConfigurationFactory<T extends Configuration> {
    /**
     * Parses the provided {@code bytes} and returns a configuration object.
     *
     * @throws ConfigStoreException if parsing fails
     */
    T parse(byte[] bytes) throws ConfigStoreException;
}
