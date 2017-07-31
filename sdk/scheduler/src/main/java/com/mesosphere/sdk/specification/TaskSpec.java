package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.SpecVisitorException;

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

    @JsonProperty("kill-grace-period")
    Integer getTaskKillGracePeriodSeconds();

    @JsonProperty("transport-encryption")
    Collection<TransportEncryptionSpec> getTransportEncryption();

    static String getInstanceName(PodInstance podInstance, TaskSpec taskSpec) {
        return getInstanceName(podInstance, taskSpec.getName());
    }

    static String getInstanceName(PodInstance podInstance, String taskName) {
        return podInstance.getName() + "-" + taskName;
    }

    default void accept(SpecVisitor specVisitor) throws SpecVisitorException {
        specVisitor.visit(this);
        for (ResourceSpec resourceSpec : getResourceSet().getResources()) {
            resourceSpec.accept(specVisitor);
        }

        for (VolumeSpec volumeSpec : getResourceSet().getVolumes()) {
            volumeSpec.accept(specVisitor);
        }
        specVisitor.finalizeVisit(this);
    }
}
