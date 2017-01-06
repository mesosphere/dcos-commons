package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.config.Configuration;

import java.util.List;

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

    @JsonProperty("pod-specs")
    List<PodSpec> getPods();

    @JsonProperty("api-port")
    int getApiPort();

    @JsonProperty("zookeeper")
    String getZookeeperConnection();

    @JsonProperty("replacement-failure-policy")
    ReplacementFailurePolicy getReplacementFailurePolicy();
}
