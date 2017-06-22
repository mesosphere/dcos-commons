package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.config.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * Defines a {@link Service}.
 */
public interface ServiceSpec extends Configuration {
    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("principal")
    String getPrincipal();

    @JsonProperty("user")
    String getUser();

    @JsonProperty("pod-specs")
    List<PodSpec> getPods();

    @JsonProperty("api-port")
    int getApiPort();

    @JsonProperty("web-url")
    String getWebUrl();

    @JsonProperty("zookeeper")
    String getZookeeperConnection();

    @JsonProperty("replacement-failure-policy")
    Optional<ReplacementFailurePolicy> getReplacementFailurePolicy();
}
