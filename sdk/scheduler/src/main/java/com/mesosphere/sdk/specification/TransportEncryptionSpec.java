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

  @JsonProperty("mount-path")
  Optional<String> getMountPath();

  @JsonProperty("provisioning")
  Provisioning getProvisioning();

  /**
   * The allowed formats of TLS certificate format.
   */
  enum Type {
    // TODO(mh): Rename to PEM ?
    TLS,
    KEYSTORE,
    // TODO@kjoshi remove later.
    CUSTOM
  }

  /**
   * Whether SDK should generate artifacts (default)
   * or use provided ones from the Secret-Store.
   */
  enum Provisioning {
    GENERATE,
    SECRET_STORE
  }
}
