package com.mesosphere.sdk.storage;

/**
 * Container for types related to {@link com.mesosphere.sdk.config.ConfigStoreException}s and
 * {@link com.mesosphere.sdk.state.StateStoreException}s.
 */
public class StorageError {

    private StorageError() {
        // do not instantiate
    }

    /**
     * Machine-parseable indicator of the cause for a storage-related exception.
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
}
