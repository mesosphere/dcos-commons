package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This interface defines requirements for objects which wish to be stored in the ConfigStore.
 */
public interface Configuration {
    /**
     * Returns a byte representation of this Configuration which is suitable for writing to disk.
     */
    @JsonIgnore
    byte[] getBytes() throws ConfigStoreException;

    /**
     * Returns a JSON representation of this Configuration which is suitable for displaying to the
     * user.
     */
    @JsonIgnore
    String toJsonString() throws Exception;

    /**
     * Returns whether this configuration is overall equivalent to the provided configuration.
     * Required for interaction with {@link ConfigurationUpdater}.
     */
    @Override
    boolean equals(Object other);
}
