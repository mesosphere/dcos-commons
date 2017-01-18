package com.mesosphere.sdk.config;

import java.io.IOException;

/**
 * Exception that indicates that there was an issue with storing values
 * in the config store.  The underlying exception is intended to be
 * nested for developer understanding.
 */
public class ConfigStoreException extends IOException {

    /**
     * Machine-parseable indicator of the cause for an exception.
     */
    public enum Reason {

        /**
         * Cause is unexpected or reason could not be determined.
         */
        UNKNOWN,

        /**
         * The requested data was not found.
         */
        NOT_FOUND,

        /**
         * The underlying storage failed to store or retrieve the requested data.
         */
        STORAGE_ERROR,

        /**
         * The data could not be serialized or deserialized into a format suitable for storage.
         */
        SERIALIZATION_ERROR,

        /**
         * The requested operation is invalid.
         */
        LOGIC_ERROR
    }

    private final Reason reason;

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
