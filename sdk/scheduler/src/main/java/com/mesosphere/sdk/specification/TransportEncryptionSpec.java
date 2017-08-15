package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Spec for defining a TLS encryption support.
 */
@JsonDeserialize(as = DefaultTransportEncryptionSpec.class)
public interface TransportEncryptionSpec {
    @JsonProperty("name")
    String getName();

    @JsonProperty("type")
    Type getType();

    /**
     * The allowed formats of TLS certificate format.
     */
    enum Type {
        TLS, // TODO(mh): Rename to PEM ?
        KEYSTORE
    }
}
