package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * Default implementation of a {@link TaskSpec}.
 */
public class DefaultTaskSpec implements TaskSpec {
    @NotNull
    @Size(min = 1)
    private String name;

    @NotNull
    private GoalState goalState;

    @Valid
    private CommandSpec commandSpec;

    @Valid
    private HealthCheckSpec healthCheckSpec;

    @Valid
    private ReadinessCheckSpec readinessCheckSpec;

    @Valid
    @NotNull
    private ResourceSet resourceSet;

    private Collection<URI> uris;
    private Collection<ConfigFileSpec> configFiles;

    @JsonCreator
    public DefaultTaskSpec(
            @JsonProperty("name") String name,
            @JsonProperty("goal") GoalState goalState,
            @JsonProperty("resource-set") ResourceSet resourceSet,
            @JsonProperty("command-spec") CommandSpec commandSpec,
            @JsonProperty("health-check-spec") HealthCheckSpec healthCheckSpec,
            @JsonProperty("readiness-check-spec") ReadinessCheckSpec readinessCheckSpec,
            @JsonProperty("uris") Collection<URI> uris,
            @JsonProperty("config-files") Collection<ConfigFileSpec> configFiles) {
        this.name = name;
        this.goalState = goalState;
        this.resourceSet = resourceSet;
        this.commandSpec = commandSpec;
        this.healthCheckSpec = healthCheckSpec;
        this.readinessCheckSpec = readinessCheckSpec;
        this.uris = uris;
        this.configFiles = configFiles;
    }

    private DefaultTaskSpec(Builder builder) {
        this(
                builder.name,
                builder.goalState,
                builder.resourceSet,
                builder.commandSpec,
                builder.healthCheckSpec,
                builder.readinessCheckSpec,
                builder.uris,
                builder.configFiles);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(TaskSpec copy) {
        Builder builder = new Builder();
        builder.name = copy.getName();
        builder.goalState = copy.getGoal();
        builder.resourceSet = copy.getResourceSet();
        builder.commandSpec = copy.getCommand().orElse(null);
        builder.healthCheckSpec = copy.getHealthCheck().orElse(null);
        builder.uris = copy.getUris();
        builder.configFiles = copy.getConfigFiles();
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
    public ResourceSet getResourceSet() {
        return resourceSet;
    }

    @Override
    public Optional<CommandSpec> getCommand() {
        return Optional.ofNullable(commandSpec);
    }

    @Override
    public Optional<HealthCheckSpec> getHealthCheck() {
        return Optional.ofNullable(healthCheckSpec);
    }

    @Override
    public Optional<ReadinessCheckSpec> getReadinessCheck() {
        return Optional.ofNullable(readinessCheckSpec);
    }

    @Override
    public Collection<URI> getUris() {
        return uris;
    }

    @Override
    public Collection<ConfigFileSpec> getConfigFiles() {
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
        private GoalState goalState;
        private ResourceSet resourceSet;
        private CommandSpec commandSpec;
        private HealthCheckSpec healthCheckSpec;
        private ReadinessCheckSpec readinessCheckSpec;
        private Collection<URI> uris;
        private Collection<ConfigFileSpec> configFiles;

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
         * Sets the {@code goalState} and returns a reference to this Builder so that the methods can be
         * chained together.
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
         * Sets the {@code commandSpec} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param commandSpec the {@code commandSpec} to set
         * @return a reference to this Builder
         */
        public Builder commandSpec(CommandSpec commandSpec) {
            this.commandSpec = commandSpec;
            return this;
        }

        /**
         * Sets the {@code healthCheckSpec} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param healthCheckSpec the {@code healthCheckSpec} to set
         * @return a reference to this Builder
         */
        public Builder healthCheckSpec(HealthCheckSpec healthCheckSpec) {
            this.healthCheckSpec = healthCheckSpec;
            return this;
        }

        /**
         * Sets the {@code readinessChecksSpec} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param readinessCheckSpec the {@code readinessCheckSpec} to set
         * @return a reference to this Builder
         */
        public Builder readinessCheckSpec(ReadinessCheckSpec readinessCheckSpec) {
            this.readinessCheckSpec = readinessCheckSpec;
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
         * Sets the {@code configFiles} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param configFiles the {@code configFiles} to set
         * @return a reference to this Builder
         */
        public Builder configFiles(Collection<ConfigFileSpec> configFiles) {
            this.configFiles = configFiles;
            return this;
        }

        /**
         * Returns a {@code DefaultTaskSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultTaskSpec} built with parameters of this {@code DefaultTaskSpec.Builder}
         */
        public DefaultTaskSpec build() {
            DefaultTaskSpec defaultTaskSpec = new DefaultTaskSpec(this);
            ValidationUtils.validate(defaultTaskSpec);
            return defaultTaskSpec;
        }
    }
}
