package org.apache.mesos.config;

/**
 * This interface defines a way for previously stored Configuration objects to be reconstructed from a byte array.
 *
 * @param <T> The {@code Configuration} type that will be parsed.
 */
public interface ConfigurationFactory<T extends Configuration> {
    T parse(byte[] bytes);
}
