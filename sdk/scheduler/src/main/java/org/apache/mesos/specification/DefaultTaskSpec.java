package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.offer.TaskUtils;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

public class DefaultTaskSpec implements TaskSpec {
    private String name;
    private String type;
    private GoalState goalState;
    private CommandSpec commandSpec;
    private ContainerSpec containerSpec;
    private HealthCheckSpec healthCheckSpec;
    private Collection<URI> uris;
    private Collection<ConfigFileSpecification> configFiles;
    private ResourceSet resourceSet;

    @JsonCreator
    public DefaultTaskSpec(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("goal") GoalState goalState,
            @JsonProperty("resource_set") ResourceSet resourceSet,
            @JsonProperty("command_spec") CommandSpec commandSpec,
            @JsonProperty("container_spec") ContainerSpec containerSpec,
            @JsonProperty("health_check_spec") HealthCheckSpec healthCheckSpec,
            @JsonProperty("uris") Collection<URI> uris,
            @JsonProperty("config_files") Collection<ConfigFileSpecification> configFiles) {
        this.name = name;
        this.type = type;
        this.goalState = goalState;
        this.resourceSet= resourceSet;
        this.commandSpec = commandSpec;
        this.containerSpec = containerSpec;
        this.healthCheckSpec = healthCheckSpec;
        this.uris = uris;
        this.configFiles = configFiles;
    }

    private DefaultTaskSpec(Builder builder) {
        this(
                builder.name,
                builder.type,
                builder.goalState,
                builder.resourceSet,
                builder.commandSpec,
                builder.containerSpec,
                builder.healthCheckSpec,
                builder.uris,
                builder.configFiles);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultTaskSpec copy) {
        Builder builder = new Builder();
        builder.name = copy.name;
        builder.goalState = copy.goalState;
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
    public String getType() {
        return type;
    }

    @Override
    public GoalState getGoal() {
        return goalState;
    }

    @Override
    public ResourceSet getResourceSet() {
        return resourceSet;
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

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TaskSpec)) {
            return false;
        }
        return !TaskUtils.areDifferent(this, (TaskSpec) o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }


    /**
     * {@code DefaultTaskSpec} builder static inner class.
     */
    public static final class Builder {
        private String name;
        private String type;
        private GoalState goalState;
        private ResourceSet resourceSet;
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

        public Builder type(String type) {
            this.type = type;
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

        public Builder resourceSet(ResourceSet resourceSet) {
            this.resourceSet = resourceSet;
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
