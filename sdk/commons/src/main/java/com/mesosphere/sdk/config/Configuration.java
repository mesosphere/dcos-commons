package com.mesosphere.sdk.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
     */
    @JsonIgnore
    default byte[] getBytes() throws ConfigStoreException {
        try {
            return toJsonString().getBytes(CHARSET);
        } catch (Exception e) {
            throw new ConfigStoreException("Failed to get JSON representation of service spec: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a JSON representation of this Configuration which is suitable for displaying to the
     * user.
     */
    @JsonIgnore
    default String toJsonString() throws ConfigStoreException {
        try {
            return SerializationUtils.toJsonString(this);
        } catch (IOException e) {
            throw new ConfigStoreException(e);
        }
    }
}
