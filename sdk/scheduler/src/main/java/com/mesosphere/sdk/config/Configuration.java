package com.mesosphere.sdk.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mesosphere.sdk.storage.StorageError.Reason;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * This interface defines requirements for objects which wish to be stored in the ConfigStore.
 */
public interface Configuration {
    Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Returns a byte representation of this Configuration which is suitable for writing to disk.
     *
     * @throws ConfigStoreException if serialization fails
     */
    @JsonIgnore
    default byte[] getBytes() throws ConfigStoreException {
        try {
            return toJsonString().getBytes(CHARSET);
        } catch (Exception e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR,
                    "Failed to get JSON representation of service spec: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a JSON representation of this Configuration which is suitable for displaying to the
     * user.
     *
     * @throws ConfigStoreException if deserialization fails
     */
    @JsonIgnore
    default String toJsonString() throws ConfigStoreException {
        try {
            return SerializationUtils.toJsonString(this);
        } catch (IOException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, e);
        }
    }
}
