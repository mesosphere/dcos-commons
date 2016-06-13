package org.apache.mesos.config;

import java.io.IOException;

/**
 * Exception that indicates that there was an issue with storing values
 * in the config store.  The underlying exception is intended to be
 * nested for developer understanding.
 */
public class ConfigStoreException extends IOException {

    public ConfigStoreException(Exception e) {
        super(e);
    }

    public ConfigStoreException(String message) {
        super(message);
    }

    public ConfigStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
