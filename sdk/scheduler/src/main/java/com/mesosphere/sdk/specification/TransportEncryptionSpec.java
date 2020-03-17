package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;

/**
 * Spec for defining a TLS encryption support.
 */
@JsonDeserialize(as = DefaultTransportEncryptionSpec.class)
public interface TransportEncryptionSpec {
  @JsonProperty("name")
  String getName();

  @JsonProperty("type")
  Type getType();

  @JsonProperty("secret")
  Optional<String> getSecret();

  /**
   * The allowed formats of TLS certificate format.
   */
  enum Type {
    // TODO(mh): Rename to PEM ?
    TLS,
    KEYSTORE,
    CUSTOM
  }
}
