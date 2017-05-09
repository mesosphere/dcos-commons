package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.specification.util.RLimit;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A SecretSpec defines the features of a Secret.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface SecretSpec extends ResourceSpec {

    @JsonProperty("secret")
    Optional<String> getSecret();

    @JsonProperty("env-key")
    Optional<String> getEnvKey();

    @JsonProperty("file")
    Optional<String> getFilePath();

}
