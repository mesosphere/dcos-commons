package com.mesosphere.sdk.storage;

import java.io.IOException;

import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Exception that indicates that there was an issue with storing or accessing values in the persister.
 * The underlying exception from the storage implementation, if any, is intended to nested inside this exception for
 * developer understanding.
 */
public class PersisterException extends IOException {

    private final Reason reason;

    public PersisterException(Reason reason, Throwable e) {
        super(e);
        this.reason = reason;
    }

    public PersisterException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PersisterException(Reason reason, String message, Throwable cause) {
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
