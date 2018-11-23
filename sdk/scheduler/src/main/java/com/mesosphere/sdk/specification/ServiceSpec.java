package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.config.Configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Defines a Service's configuration.
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

  @JsonProperty("goal")
  GoalState getGoal();

  @JsonProperty("region")
  Optional<String> getRegion();

  @JsonProperty("web-url")
  String getWebUrl();

  @JsonProperty("zookeeper")
  String getZookeeperConnection();

  @JsonProperty("replacement-failure-policy")
  Optional<ReplacementFailurePolicy> getReplacementFailurePolicy();

  @JsonProperty("pod-specs")
  List<PodSpec> getPods();
}
