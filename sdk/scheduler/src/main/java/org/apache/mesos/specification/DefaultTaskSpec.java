package org.apache.mesos.specification;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

public class DefaultTaskSpec implements TaskSpec {
    private String name;
    private GoalState goalState;
    private String resourceSetId;
    private PodSpec pod;
    private CommandSpec commandSpec;
    private ContainerSpec containerSpec;
    private HealthCheckSpec healthCheckSpec;
    private Collection<URI> uris;
    private Collection<ConfigFileSpecification> configFiles;

    private DefaultTaskSpec(Builder builder) {
        name = builder.name;
        goalState = builder.goalState;
        resourceSetId = builder.resourceSetId;
        pod = builder.pod;
        commandSpec = builder.commandSpec;
        containerSpec = builder.containerSpec;
        healthCheckSpec = builder.healthCheckSpec;
        uris = builder.uris;
        configFiles = builder.configFiles;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultTaskSpec copy) {
        Builder builder = new Builder();
        builder.name = copy.name;
        builder.goalState = copy.goalState;
        builder.resourceSetId = copy.resourceSetId;
        builder.pod = copy.pod;
        builder.commandSpec = copy.commandSpec;
        builder.containerSpec = copy.containerSpec;
        builder.healthCheckSpec = copy.healthCheckSpec;
        builder.uris = copy.uris;
        builder.configFiles = copy.configFiles;
        return builder;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GoalState getGoal() {
        return goalState;
    }

    @Override
    public String getResourceSetId() {
        return resourceSetId;
    }

    @Override
    public PodSpec getPod() {
        return pod;
    }

    @Override
    public Optional<CommandSpec> getCommand() {
        return Optional.ofNullable(commandSpec);
    }

    @Override
    public Optional<ContainerSpec> getContainer() {
        return Optional.ofNullable(containerSpec);
    }

    @Override
    public Optional<HealthCheckSpec> getHealthCheck() {
        return Optional.ofNullable(healthCheckSpec);
    }

    @Override
    public Collection<URI> getUris() {
        return uris;
    }

    @Override
    public Collection<ConfigFileSpecification> getConfigFiles() {
        return configFiles;
    }


    /**
     * {@code DefaultTaskSpec} builder static inner class.
     */
    public static final class Builder {
        private String name;
        private GoalState goalState;
        private String resourceSetId;
        private PodSpec pod;
        private CommandSpec commandSpec;
        private ContainerSpec containerSpec;
        private HealthCheckSpec healthCheckSpec;
        private Collection<URI> uris;
        private Collection<ConfigFileSpecification> configFiles;

        private Builder() {
        }

        /**
         * Sets the {@code name} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param name the {@code name} to set
         * @return a reference to this Builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the {@code goalState} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param goalState the {@code goalState} to set
         * @return a reference to this Builder
         */
        public Builder goalState(GoalState goalState) {
            this.goalState = goalState;
            return this;
        }

        /**
         * Sets the {@code resourceSetId} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param resourceSetId the {@code resourceSetId} to set
         * @return a reference to this Builder
         */
        public Builder resourceSetId(String resourceSetId) {
            this.resourceSetId = resourceSetId;
            return this;
        }

        /**
         * Sets the {@code pod} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param pod the {@code pod} to set
         * @return a reference to this Builder
         */
        public Builder pod(PodSpec pod) {
            this.pod = pod;
            return this;
        }

        /**
         * Sets the {@code commandSpec} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param commandSpec the {@code commandSpec} to set
         * @return a reference to this Builder
         */
        public Builder commandSpec(CommandSpec commandSpec) {
            this.commandSpec = commandSpec;
            return this;
        }

        /**
         * Sets the {@code containerSpec} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param containerSpec the {@code containerSpec} to set
         * @return a reference to this Builder
         */
        public Builder containerSpec(ContainerSpec containerSpec) {
            this.containerSpec = containerSpec;
            return this;
        }

        /**
         * Sets the {@code healthCheckSpec} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param healthCheckSpec the {@code healthCheckSpec} to set
         * @return a reference to this Builder
         */
        public Builder healthCheckSpec(HealthCheckSpec healthCheckSpec) {
            this.healthCheckSpec = healthCheckSpec;
            return this;
        }

        /**
         * Sets the {@code uris} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param uris the {@code uris} to set
         * @return a reference to this Builder
         */
        public Builder uris(Collection<URI> uris) {
            this.uris = uris;
            return this;
        }

        /**
         * Sets the {@code configFiles} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param configFiles the {@code configFiles} to set
         * @return a reference to this Builder
         */
        public Builder configFiles(Collection<ConfigFileSpecification> configFiles) {
            this.configFiles = configFiles;
            return this;
        }

        /**
         * Returns a {@code DefaultTaskSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultTaskSpec} built with parameters of this {@code DefaultTaskSpec.Builder}
         */
        public DefaultTaskSpec build() {
            return new DefaultTaskSpec(this);
        }
    }
}
