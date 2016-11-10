package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * Created by gabriel on 11/7/16.
 */

@JsonDeserialize(as = DefaultTaskSpec.class)
public interface TaskSpec {
    @JsonProperty("name")
    String getName();

    @JsonProperty("goal")
    GoalState getGoal();

    @JsonProperty("resource_set_id")
    String getResourceSetId();

    // This method must be ignored to avoid infinite recursion, during serialization.  TaskSpecs should be constructed
    // with reference to their parent Pod which should be derived from structure of ServiceSpecification stored in the
    // ConfigStore.
    @JsonIgnore
    PodSpec getPod();

    @JsonProperty("command_spec")
    Optional<CommandSpec> getCommand();

    @JsonProperty("container_spec")
    Optional<ContainerSpec> getContainer();

    @JsonProperty("health_check_spec")
    Optional<HealthCheckSpec> getHealthCheck();

    @JsonProperty("uris")
    Collection<URI> getUris();

    @JsonProperty("config_files")
    Collection<ConfigFileSpecification> getConfigFiles();

    /**
     * The allowed goal states for a Task.
     */
    enum GoalState {
        RUNNING,
        FINISHED
    }
}
