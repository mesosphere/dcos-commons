package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Created by gabriel on 11/7/16.
 */
public interface TaskSpec {
    String getName();
    GoalState getGoal();
    String getResourceSetId();

    // This method must be ignored to avoid infinite recursion, during serialization.  TaskSpecs should be constructed
    // with reference to their parent Pod which should be derived from structure of ServiceSpecification stored in the
    // ConfigStore.
    @JsonIgnore
    PodSpec getPod();

    Optional<String> getCommand();
    Optional<ContainerSpec> getContainer();

    Map<String, String> getEnvironment();

    Optional<HealthCheckSpec> getHealthCheck();
    Collection<URI> getUris();
    Collection<ConfigFileSpecification> getConfigFiles();

    enum GoalState {
        RUNNING,
        FINISHED
    }
}
