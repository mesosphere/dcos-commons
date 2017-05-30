package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.Optional;

/**
 * Specification for a Task.
 */
@JsonDeserialize(as = DefaultTaskSpec.class)
public interface TaskSpec {
    @JsonProperty("name")
    String getName();

    @JsonProperty("goal")
    GoalState getGoal();

    @JsonProperty("resource-set")
    ResourceSet getResourceSet();

    @JsonProperty("command-spec")
    Optional<CommandSpec> getCommand();

    @JsonProperty("health-check-spec")
    Optional<HealthCheckSpec> getHealthCheck();

    @JsonProperty("readiness-check-spec")
    Optional<ReadinessCheckSpec> getReadinessCheck();

    @JsonProperty("config-files")
    Collection<ConfigFileSpec> getConfigFiles();

    @JsonProperty("discovery-spec")
    Optional<DiscoverySpec> getDiscovery();

    static String getInstanceName(PodInstance podInstance, TaskSpec taskSpec) {
        return getInstanceName(podInstance, taskSpec.getName());
    }

    static String getInstanceName(PodInstance podInstance, String taskName) {
        return podInstance.getName() + "-" + taskName;
    }
}
