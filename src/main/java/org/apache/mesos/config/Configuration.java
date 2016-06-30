package org.apache.mesos.config;

/**
 * This interface defines requirements for objects which wish to be stored in the ConfigStore.
 */
public interface Configuration {
    /**
     * Returns a byte representation of this Configuration which is suitable for writing to disk.
     */
    byte[] getBytes() throws ConfigStoreException;

    /**
     * Returns a JSON representation of this Configuration which is suitable for displaying to the
     * user.
     */
    String toJsonString() throws Exception;
}
