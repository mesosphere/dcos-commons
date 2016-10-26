package org.apache.mesos.config;

/**
 * This interface defines a way to compare two Configuration objects for equality. Intentionally
 * separate from {@link Object#equals(Object)} to be very explicit about this requirement.
 *
 * @param <T> The {@code Configuration} type that will be parsed.
 */
public interface ConfigurationComparer<T extends Configuration> {
    /**
     * Returns whether the provided configuration objects are equal.
     */
    boolean equals(T first, T second);
}
