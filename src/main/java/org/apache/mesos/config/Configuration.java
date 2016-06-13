package org.apache.mesos.config;

/**
 * This interface defines requirements for objects which wish to be stored in the ConfigStore.
 */
public interface Configuration {
    byte[] getBytes();
}
