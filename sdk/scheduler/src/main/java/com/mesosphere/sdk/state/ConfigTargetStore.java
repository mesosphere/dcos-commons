package com.mesosphere.sdk.state;

import java.util.UUID;

/**
 * This interface defines the subset of {@link ConfigStore} operations which are applicable to
 * setting and retrieving the {@link UUID} of the current target configuration.
 *
 * This interface is broken out from {@link ConfigStore} to allow users to ignore the the generic
 * typing of the config objects themselves when they don't need to access those objects.
 */
public interface ConfigTargetStore {

    /**
     * Stores the ID of the active target configuration, replacing any current value.
     *
     * @see #getTargetConfig()
     * @throws ConfigStoreException if writing the ID fails
     */
    void setTargetConfig(UUID id) throws ConfigStoreException;

    /**
     * Returns the current ID of the active target configuration, or throws an exception if none is
     * set.
     *
     * @see #setTargetConfig(UUID)
     * @throws ConfigStoreException if reading or deserializing the ID fails, or no value is set
     */
    UUID getTargetConfig() throws ConfigStoreException;
}
