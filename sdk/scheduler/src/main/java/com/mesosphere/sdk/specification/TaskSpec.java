package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.net.URI;
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

    @JsonProperty("resource_set")
    ResourceSet getResourceSet();

    @JsonProperty("command_spec")
    Optional<CommandSpec> getCommand();

    @JsonProperty("health_check_spec")
    Optional<HealthCheckSpec> getHealthCheck();

    @JsonProperty("uris")
    Collection<URI> getUris();

    @JsonProperty("config_files")
    Collection<ConfigFileSpecification> getConfigFiles();

    static String getInstanceName(PodInstance podInstance, TaskSpec taskSpec) {
        return podInstance.getName() + "-" + taskSpec.getName();
    }
}
