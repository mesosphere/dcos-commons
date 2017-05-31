package com.mesosphere.sdk.config;

import java.io.IOException;

import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Exception that indicates that there was an issue with storing values
 * in the config store.  The underlying exception is intended to be
 * nested for developer understanding.
 */
public class ConfigStoreException extends IOException {

    private final Reason reason;

    public ConfigStoreException(PersisterException e) {
        super(e);
        this.reason = Reason.STORAGE_ERROR;
    }

    public ConfigStoreException(PersisterException e, String message) {
        super(message, e);
        this.reason = Reason.STORAGE_ERROR;
    }

    public ConfigStoreException(Reason reason, Throwable cause) {
        super(cause);
        this.reason = reason;
    }

    public ConfigStoreException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ConfigStoreException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the machine-parseable reason for this exception. Primarily used for debugging/logging purposes and for
     * delineating different error cases in REST APIs.
     */
    public Reason getReason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return String.format("%s (reason: %s)", super.getMessage(), reason);
    }
}
