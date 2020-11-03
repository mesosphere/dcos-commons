package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.mesos.Protos;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Specification for a Pod.
 */
@JsonDeserialize(as = DefaultPodSpec.class)
public interface PodSpec {
  @JsonIgnore
  static String getName(PodSpec podSpec, int index) {
    return podSpec.getType() + "-" + index;
  }

  @JsonProperty("type")
  String getType();

  @JsonProperty("count")
  Integer getCount();

  @JsonProperty("allow-decommission")
  Boolean getAllowDecommission();

  @JsonProperty("image")
  Optional<String> getImage();

  @JsonProperty("networks")
  Collection<NetworkSpec> getNetworks();

  @JsonProperty("rlimits")
  Collection<RLimitSpec> getRLimits();

  @JsonProperty("uris")
  Collection<URI> getUris();

  @JsonProperty("user")
  Optional<String> getUser();

  @JsonProperty("task-specs")
  List<TaskSpec> getTasks();

  @JsonProperty("placement-rule")
  Optional<PlacementRule> getPlacementRule();

  @JsonProperty("volumes")
  Collection<VolumeSpec> getVolumes();

  @JsonProperty("pre-reserved-role")
  String getPreReservedRole();

  @JsonProperty("secrets")
  Collection<SecretSpec> getSecrets();

  @JsonProperty("share-pid-namespace")
  Boolean getSharePidNamespace();

  @JsonProperty("host-volumes")
  Collection<HostVolumeSpec> getHostVolumes();

  @JsonProperty("external-volumes")
  Collection<ExternalVolumeSpec> getExternalVolumes();

  @JsonProperty("seccomp-unconfined")
  Boolean getSeccompUnconfined();

  @JsonProperty("seccomp-profile-name")
  Optional<String> getSeccompProfileName();

  @JsonProperty("ipc-mode")
  Optional<Protos.LinuxInfo.IpcMode> getSharedMemory();

  @JsonProperty("shm-size")
  Optional<Integer> getSharedMemorySize();
}
