package com.mesosphere.sdk.state;

import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Exception that indicates that there was an issue with storing or accessing values in the state store.
 * The underlying exception from the storage implementation, if any, is intended to nested inside this exception for
 * developer understanding.
 */
public class StateStoreException extends RuntimeException {

    private final Reason reason;

    public StateStoreException(PersisterException e) {
        super(e);
        this.reason = Reason.STORAGE_ERROR;
    }

    public StateStoreException(PersisterException e, String message) {
        super(message, e);
        this.reason = Reason.STORAGE_ERROR;
    }

    public StateStoreException(Reason reason, Throwable e) {
        super(e);
        this.reason = reason;
    }

    public StateStoreException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StateStoreException(Reason reason, String message, Throwable cause) {
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
