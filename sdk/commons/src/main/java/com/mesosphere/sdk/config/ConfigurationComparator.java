package com.mesosphere.sdk.config;

/**
 * This interface defines a way to compare two {@link Configuration} objects for equality. This
 * interface is intentionally separate from {@link Object#equals(Object)} in order to have very
 * explicit requirements around equality checks when they're needed.
 *
 * @param <T> The {@code Configuration} type that will be parsed.
 */
public interface ConfigurationComparator<T extends Configuration> {
    /**
     * Returns whether the provided configuration objects are equal.
     */
    boolean equals(T first, T second);
}
