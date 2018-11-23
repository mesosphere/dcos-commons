package com.mesosphere.sdk.state;

import java.util.UUID;

/**
 * This interface defines the subset of {@link ConfigStore} operations which are applicable to
 * setting and retrieving the {@link UUID} of the current target configuration.
 * <p>
 * This interface is broken out from {@link ConfigStore} to allow users to ignore the the generic
 * typing of the config objects themselves when they don't need to access those objects.
 */
public interface ConfigTargetStore {

  /**
   * Returns the current ID of the active target configuration, or throws an exception if none is
   * set.
   *
   * @throws ConfigStoreException if reading or deserializing the ID fails, or no value is set
   * @see #setTargetConfig(UUID)
   */
  UUID getTargetConfig() throws ConfigStoreException;

  /**
   * Stores the ID of the active target configuration, replacing any current value.
   *
   * @throws ConfigStoreException if writing the ID fails
   * @see #getTargetConfig()
   */
  void setTargetConfig(UUID id) throws ConfigStoreException;
}
