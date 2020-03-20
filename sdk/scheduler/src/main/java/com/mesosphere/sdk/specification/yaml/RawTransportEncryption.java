package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML transport encryption.
 */
public final class RawTransportEncryption {
  private final String name;

  private final String type;

  private final String secret;

  private RawTransportEncryption(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("secret") String secret)
  {
    this.name = name;
    this.type = type;
    this.secret = secret;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getSecret() {
    return secret;
  }
}
