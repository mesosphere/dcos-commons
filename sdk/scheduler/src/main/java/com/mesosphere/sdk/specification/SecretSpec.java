package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;

/**
 * A SecretSpec defines the features of a Secret.
 */
@JsonDeserialize(as = DefaultSecretSpec.class)
public interface SecretSpec {

    @JsonProperty("secret")
    String getSecretPath();

    @JsonProperty("env-key")
    Optional<String> getEnvKey();

    @JsonProperty("file")
    Optional<String> getFilePath();

}
