package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Specification for a Task.
 */
@JsonDeserialize(as = DefaultTaskSpec.class)
public interface TaskSpec {
  static String getInstanceName(PodInstance podInstance, TaskSpec taskSpec) {
    return getInstanceName(podInstance, taskSpec.getName());
  }

  static String getInstanceName(PodInstance podInstance, String taskName) {
    return podInstance.getName() + "-" + taskName;
  }

  @JsonProperty("name")
  String getName();

  @JsonProperty("goal")
  GoalState getGoal();

  @JsonProperty("essential")
  Boolean isEssential();

  @JsonProperty("resource-set")
  ResourceSet getResourceSet();

  @JsonProperty("command-spec")
  Optional<CommandSpec> getCommand();

  @JsonProperty("task-labels")
  Map<String, String> getTaskLabels();

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

  @JsonProperty("ipc-mode")
  Optional<Protos.LinuxInfo.IpcMode> getSharedMemory();

  @JsonProperty("shm-size")
  Optional<Integer> getSharedMemorySize();
}
