package org.apache.mesos.specification;

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
    String getResourceSet();

    Optional<String> getCommand();
    Optional<ContainerSpec> getContainer();

    Map<String, String> getEnvironment();

    Optional<HealthCheckSpec> getHealthCheck();
    Collection<URI> getUris();
    Collection<ConfigFileSpecification> getConfigurations();

    enum GoalState {
        RUNNING,
        FINISHED
    }
}
