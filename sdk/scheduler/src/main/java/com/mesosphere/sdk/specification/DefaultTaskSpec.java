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
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Default implementation of a {@link TaskSpec}.
 */
public class DefaultTaskSpec implements TaskSpec {
    @NotNull
    @Size(min = 1)
    private final String name;

    @NotNull
    private final GoalState goalState;

    @Valid
    private final CommandSpec commandSpec;

    @Valid
    private final HealthCheckSpec healthCheckSpec;

    @Valid
    private final ReadinessCheckSpec readinessCheckSpec;

    @Valid
    @NotNull
    private final ResourceSet resourceSet;

    @Valid
    private final DiscoverySpec discoverySpec;

    @Valid
    private final Collection<ConfigFileSpec> configFiles;

    @Valid
    private Collection<TransportEncryptionSpec> transportEncryption;

    @JsonCreator
    public DefaultTaskSpec(
            @JsonProperty("name") String name,
            @JsonProperty("goal") GoalState goalState,
            @JsonProperty("resource-set") ResourceSet resourceSet,
            @JsonProperty("command-spec") CommandSpec commandSpec,
            @JsonProperty("health-check-spec") HealthCheckSpec healthCheckSpec,
            @JsonProperty("readiness-check-spec") ReadinessCheckSpec readinessCheckSpec,
            @JsonProperty("config-files") Collection<ConfigFileSpec> configFiles,
            @JsonProperty("discovery-spec") DiscoverySpec discoverySpec,
            @JsonProperty("transport-encryption") Collection<TransportEncryptionSpec> transportEncryption) {
        this.name = name;
        this.goalState = goalState;
        this.resourceSet = resourceSet;
        this.commandSpec = commandSpec;
        this.healthCheckSpec = healthCheckSpec;
        this.readinessCheckSpec = readinessCheckSpec;
        this.configFiles = (configFiles != null) ? configFiles : Collections.emptyList();
        this.discoverySpec = discoverySpec;
        this.transportEncryption = (transportEncryption != null) ? transportEncryption : Collections.emptyList();
    }

    private DefaultTaskSpec(Builder builder) {
        this(
                builder.name,
                builder.goalState,
                builder.resourceSet,
                builder.commandSpec,
                builder.healthCheckSpec,
                builder.readinessCheckSpec,
                builder.configFiles,
                builder.discoverySpec,
                builder.transportEncryption);
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
        builder.readinessCheckSpec(copy.getReadinessCheck().orElse(null));
        builder.healthCheckSpec = copy.getHealthCheck().orElse(null);
        builder.readinessCheckSpec = copy.getReadinessCheck().orElse(null);
        builder.configFiles = copy.getConfigFiles();
        builder.discoverySpec = copy.getDiscovery().orElse(null);
        builder.transportEncryption = copy.getTransportEncryption();
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
    public Collection<ConfigFileSpec> getConfigFiles() {
        return configFiles;
    }

    @Override
    public Optional<DiscoverySpec> getDiscovery() {
        return Optional.ofNullable(discoverySpec);
    }

    @Override
    public Collection<TransportEncryptionSpec> getTransportEncryption() {
        return transportEncryption;
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
        private Collection<ConfigFileSpec> configFiles;
        private DiscoverySpec discoverySpec;
        private Collection<TransportEncryptionSpec> transportEncryption;

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
         * Sets the {@code discoverySpec} and returns a reference to this Builder so that methods can be chained
         * together.
         *
         * @param discoverySpec The {@link DiscoverySpec} to set
         * @return a reference to this Builder
         */
        public Builder discoverySpec(DiscoverySpec discoverySpec) {
            this.discoverySpec = discoverySpec;
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

        /**
         * Sets the {@code transportEncryption} and returns a reference to this Builder so that methods can be
         * chained together.
         *
         * @param transportEncryption The {@link TransportEncryptionSpec} to set
         * @return a reference to this Builder
         */
        public Builder setTransportEncryption(Collection<TransportEncryptionSpec> transportEncryption) {
            this.transportEncryption = transportEncryption;
            return this;
        }
    }
}
