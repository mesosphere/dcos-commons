package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Default implementation of a {@link TaskSpec}.
 *
 * If you add or modify fields you must update the equals method. (technically TaskUtils.areDifferent()).
 */
public class DefaultTaskSpec implements TaskSpec {
    // TODO: paegun using a reflection-based generator test for difference (not equal) or a different method of
    // determining difference should be explored.

    @NotNull
    @Size(min = 1)
    private final String name;

    @NotNull
    private final GoalState goalState;

    @NotNull
    private final Boolean essential;

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

    @DecimalMin("0")
    @DecimalMax("1209600") //<< two weeks (product spec)
    private final int taskKillGracePeriodSeconds;

    // The default of 0s for task kill grace period is based on upgrade path,
    // matching the previous default for SDK services launched w/ DC/OS 10.9.
    public static final int TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT = 0;

    @Valid
    private Collection<TransportEncryptionSpec> transportEncryption;

    @SuppressWarnings("PMD.SimplifiedTernary")
    @JsonCreator
    public DefaultTaskSpec(
            @JsonProperty("name") String name,
            @JsonProperty("goal") GoalState goalState,
            @JsonProperty("essential") Boolean essential,
            @JsonProperty("resource-set") ResourceSet resourceSet,
            @JsonProperty("command-spec") CommandSpec commandSpec,
            @JsonProperty("health-check-spec") HealthCheckSpec healthCheckSpec,
            @JsonProperty("readiness-check-spec") ReadinessCheckSpec readinessCheckSpec,
            @JsonProperty("config-files") Collection<ConfigFileSpec> configFiles,
            @JsonProperty("discovery-spec") DiscoverySpec discoverySpec,
            @JsonProperty("kill-grace-period") Integer taskKillGracePeriodSeconds,
            @JsonProperty("transport-encryption") Collection<TransportEncryptionSpec> transportEncryption) {
        this.name = name;
        this.goalState = goalState;
        this.essential = (essential != null)
                ? essential
                : true; // default: tasks are essential
        this.resourceSet = resourceSet;
        this.commandSpec = commandSpec;
        this.healthCheckSpec = healthCheckSpec;
        this.readinessCheckSpec = readinessCheckSpec;
        this.configFiles = (configFiles != null) ? configFiles : Collections.emptyList();
        this.discoverySpec = discoverySpec;
        this.taskKillGracePeriodSeconds = (taskKillGracePeriodSeconds != null)
                ? taskKillGracePeriodSeconds
                : TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT;
        this.transportEncryption = (transportEncryption != null) ? transportEncryption : Collections.emptyList();
    }

    private DefaultTaskSpec(Builder builder) {
        this(
                builder.name,
                builder.goalState,
                builder.essential,
                builder.resourceSet,
                builder.commandSpec,
                builder.healthCheckSpec,
                builder.readinessCheckSpec,
                builder.configFiles,
                builder.discoverySpec,
                builder.taskKillGracePeriodSeconds,
                builder.transportEncryption);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(TaskSpec copy) {
        Builder builder = new Builder();
        builder.name = copy.getName();
        builder.goalState = copy.getGoal();
        builder.essential = copy.isEssential();
        builder.resourceSet = copy.getResourceSet();
        builder.commandSpec = copy.getCommand().orElse(null);
        builder.readinessCheckSpec(copy.getReadinessCheck().orElse(null));
        builder.healthCheckSpec = copy.getHealthCheck().orElse(null);
        builder.readinessCheckSpec = copy.getReadinessCheck().orElse(null);
        builder.configFiles = copy.getConfigFiles();
        builder.discoverySpec = copy.getDiscovery().orElse(null);
        builder.taskKillGracePeriodSeconds = copy.getTaskKillGracePeriodSeconds();
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
    public Boolean isEssential() {
        return essential;
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
    public Integer getTaskKillGracePeriodSeconds() {
        return taskKillGracePeriodSeconds;
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
        private Boolean essential;
        private ResourceSet resourceSet;
        private CommandSpec commandSpec;
        private HealthCheckSpec healthCheckSpec;
        private ReadinessCheckSpec readinessCheckSpec;
        private Collection<ConfigFileSpec> configFiles;
        private DiscoverySpec discoverySpec;
        private Integer taskKillGracePeriodSeconds;
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

        /**
         * Sets the {@code essential} bit and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param essential whether relaunching this task relaunches all tasks in the pod
         * @return a reference to this Builder
         */
        public Builder essential(Boolean essential) {
            this.essential = essential;
            return this;
        }

        /**
         * Sets the {@code resourceSet} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param resourceSet the {@code resourceSet} to set
         * @return a reference to this Builder
         */
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
         * Sets the {@code taskKillGracePeriodSeconds} and returns a reference to this Builder so that methods
         * can be chained together.
         *
         * @param taskKillGracePeriodSeconds The number of seconds to await the service to cleanly (gracefully)
         * shutdown following a SIGTERM signal. If the value is null or zero (0), the underlying service will be
         * sent a SIGKILL immediately.
         */
        public Builder taskKillGracePeriodSeconds(Integer taskKillGracePeriodSeconds) {
            this.taskKillGracePeriodSeconds = taskKillGracePeriodSeconds;
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
