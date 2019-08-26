package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link PodSpec}.
 */
public final class DefaultPodSpec implements PodSpec {

  private final String type;

  private final String user;

  private final Integer count;

  private final Boolean allowDecommission;

  private final String image;

  private final Collection<NetworkSpec> networks;

  private final Collection<RLimitSpec> rlimits;

  private final List<TaskSpec> tasks;

  private final PlacementRule placementRule;

  private final Collection<URI> uris;

  private final Collection<VolumeSpec> volumes;

  private final Collection<SecretSpec> secrets;

  private final String preReservedRole;

  private final Boolean sharePidNamespace;

  private final Collection<HostVolumeSpec> hostVolumes;

  private final Boolean seccompUnconfined;

  private final Optional<String> seccompProfileName;

  private final Optional<Protos.LinuxInfo.IpcMode> sharedMemory;

  private final Optional<Integer> sharedMemorySize;

  @JsonCreator
  private DefaultPodSpec(
      @JsonProperty("type") String type,
      @JsonProperty("user") String user,
      @JsonProperty("count") Integer count,
      @JsonProperty("allow-decommission") Boolean allowDecommission,
      @JsonProperty("image") String image,
      @JsonProperty("networks") Collection<NetworkSpec> networks,
      @JsonProperty("rlimits") Collection<RLimitSpec> rlimits,
      @JsonProperty("uris") Collection<URI> uris,
      @JsonProperty("task-specs") List<TaskSpec> tasks,
      @JsonProperty("placement-rule") PlacementRule placementRule,
      @JsonProperty("volumes") Collection<VolumeSpec> volumes,
      @JsonProperty("pre-reserved-role") String preReservedRole,
      @JsonProperty("secrets") Collection<SecretSpec> secrets,
      @JsonProperty("share-pid-namespace") Boolean sharePidNamespace,
      @JsonProperty("host-volumes") Collection<HostVolumeSpec> hostVolumes,
      @JsonProperty("seccomp-unconfined") Boolean seccompUnconfined,
      @JsonProperty("seccomp-profile-name") Optional<String> seccompProfileName,
      @JsonProperty("ipc-mode") Optional<Protos.LinuxInfo.IpcMode> sharedMemory,
      @JsonProperty("shm-size") Optional<Integer> sharedMemorySize)
  {
    this.type = type;
    this.user = user;
    this.count = count;
    this.allowDecommission = allowDecommission;
    this.image = image;
    this.networks = networks;
    this.rlimits = rlimits;
    this.uris = uris;
    this.tasks = tasks;
    this.placementRule = placementRule;
    this.volumes = volumes;
    this.preReservedRole = preReservedRole;
    this.secrets = secrets;
    this.sharePidNamespace = sharePidNamespace;
    this.hostVolumes = hostVolumes;
    this.seccompUnconfined = seccompUnconfined;
    this.seccompProfileName = seccompProfileName;
    this.sharedMemory = sharedMemory;
    this.sharedMemorySize = sharedMemorySize;
  }

  private DefaultPodSpec(Builder builder) {
    this(
        builder.type,
        builder.user,
        builder.count,
        builder.allowDecommission,
        builder.image,
        builder.networks,
        builder.rlimits,
        builder.uris,
        builder.tasks,
        builder.placementRule,
        builder.volumes,
        builder.preReservedRole,
        builder.secrets,
        builder.sharePidNamespace,
        builder.hostVolumes,
        builder.seccompUnconfined,
        builder.seccompProfileName,
        builder.sharedMemory,
        builder.sharedMemorySize);

    ValidationUtils.nonBlank(this, "type", type);
    ValidationUtils.nonNegative(this, "count", count);
    ValidationUtils.nonNull(this, "allowDecommission", allowDecommission);
    ValidationUtils.nonEmptyAllowNull(this, "image", image);
    ValidationUtils.nonEmpty(this, "tasks", tasks);
    ValidationUtils.nonNull(this, "sharePidNamespace", sharePidNamespace);

    Set<String> taskNames = new HashSet<>();
    for (TaskSpec taskSpec : tasks) {
      String taskName = taskSpec.getName();
      if (StringUtils.isEmpty(taskName)) {
        throw new IllegalArgumentException(
            String.format("Empty name for TaskSpec in pod %s: %s", type, taskSpec));
      } else if (taskNames.contains(taskName)) {
        throw new IllegalArgumentException(
            String.format("Duplicate task name in pod %s: %s", type, taskName));
      } else {
        taskNames.add(taskName);
      }
    }
  }

  public static Builder newBuilder(String type, int count, List<TaskSpec> tasks) {
    return new Builder(type, count, tasks);
  }

  public static Builder newBuilder(PodSpec copy) {
    Builder builder = new Builder(
        copy.getType(),
        copy.getCount(),
        copy.getTasks());
    builder.allowDecommission = copy.getAllowDecommission();
    builder.image = copy.getImage().isPresent() ? copy.getImage().get() : null;
    builder.networks = copy.getNetworks();
    builder.placementRule = copy.getPlacementRule().isPresent() ?
        copy.getPlacementRule().get() :
        null;
    builder.preReservedRole = copy.getPreReservedRole();
    builder.rlimits = copy.getRLimits();
    builder.secrets = copy.getSecrets();
    builder.uris = copy.getUris();
    builder.user = copy.getUser().isPresent() ? copy.getUser().get() : null;
    builder.volumes = copy.getVolumes();
    builder.sharePidNamespace = copy.getSharePidNamespace();
    builder.hostVolumes = copy.getHostVolumes();
    builder.seccompUnconfined = copy.getSeccompUnconfined();
    builder.seccompProfileName = copy.getSeccompProfileName();
    builder.sharedMemory = copy.getSharedMemory();
    builder.sharedMemorySize = copy.getSharedMemorySize();
    return builder;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Optional<String> getUser() {
    return Optional.ofNullable(user);
  }

  @Override
  public Integer getCount() {
    return count;
  }

  @Override
  public Boolean getAllowDecommission() {
    return allowDecommission;
  }

  @Override
  public Optional<String> getImage() {
    return Optional.ofNullable(image);
  }

  @Override
  public Collection<NetworkSpec> getNetworks() {
    return networks;
  }

  @Override
  public Collection<RLimitSpec> getRLimits() {
    return rlimits;
  }

  @Override
  public Collection<URI> getUris() {
    return uris;
  }

  @Override
  public List<TaskSpec> getTasks() {
    return tasks;
  }

  @Override
  public Optional<PlacementRule> getPlacementRule() {
    return Optional.ofNullable(placementRule);
  }

  @Override
  public Collection<VolumeSpec> getVolumes() {
    return volumes;
  }

  @Override
  public String getPreReservedRole() {
    return preReservedRole;
  }

  @Override
  public Collection<SecretSpec> getSecrets() {
    return secrets;
  }

  @Override
  public Boolean getSharePidNamespace() {
    return sharePidNamespace;
  }

  @Override
  public Collection<HostVolumeSpec> getHostVolumes() {
    return hostVolumes;
  }

  @Override
  public Boolean getSeccompUnconfined() {
    return seccompUnconfined;
  }

  @Override
  public Optional<String> getSeccompProfileName() {
    return seccompProfileName;
  }

  @Override
  public Optional<Protos.LinuxInfo.IpcMode> getSharedMemory() {
    return sharedMemory;
  }

  @Override
  public Optional<Integer> getSharedMemorySize() {
    return sharedMemorySize;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  /**
   * {@code DefaultPodSpec} builder static inner class.
   */
  public static final class Builder {
    private String type;

    private String user;

    private Integer count;

    private Boolean allowDecommission = false;

    private String image;

    private PlacementRule placementRule;

    private String preReservedRole = Constants.ANY_ROLE;

    private Collection<NetworkSpec> networks = new ArrayList<>();

    private Collection<RLimitSpec> rlimits = new ArrayList<>();

    private Collection<URI> uris = new ArrayList<>();

    private List<TaskSpec> tasks = new ArrayList<>();

    private Collection<VolumeSpec> volumes = new ArrayList<>();

    private Collection<SecretSpec> secrets = new ArrayList<>();

    private Boolean sharePidNamespace = false;

    private Collection<HostVolumeSpec> hostVolumes = new ArrayList<>();

    private Boolean seccompUnconfined = false;

    private Optional<String> seccompProfileName = Optional.empty();

    private Optional<Protos.LinuxInfo.IpcMode> sharedMemory = Optional.empty();

    private Optional<Integer> sharedMemorySize = Optional.empty();

    private Builder(String type, int count, List<TaskSpec> tasks) {
      this.type = type;
      this.count = count;
      this.tasks = new ArrayList<>(tasks);
    }

    /**
     * Sets the {@code type} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param type the {@code type} to set
     * @return a reference to this Builder
     */
    public Builder type(String type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the {@code user} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param user the {@code user} to set
     * @return a reference to this Builder
     */
    public Builder user(String user) {
      this.user = user;
      return this;
    }

    /**
     * Sets the {@code count} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param count the {@code count} to set
     * @return a reference to this Builder
     */
    public Builder count(Integer count) {
      this.count = count;
      return this;
    }

    /**
     * Sets whether the {@link #count(Integer)} for this pod can ever be decreased in a config update.
     *
     * @param allowDecommission whether the count can be decreased in a config update
     * @return a reference to this Builder
     */
    public Builder allowDecommission(Boolean allowDecommission) {
      this.allowDecommission = allowDecommission != null && allowDecommission;
      return this;
    }

    /**
     * Sets the {@code image} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param image the {@code image} to set
     * @return a reference to this Builder
     */
    public Builder image(String image) {
      this.image = image;
      return this;
    }

    /**
     * Sets the {@code networks} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param networks the {@code networks} to set
     * @return a reference to this Builder
     */
    public Builder networks(Collection<NetworkSpec> networks) {
      if (networks == null) {
        this.networks = new ArrayList<>();
      } else {
        this.networks = networks;
      }

      return this;
    }

    /**
     * Sets the {@code rlimits} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param rlimits the {@code rlimits} to set
     * @return a reference to this Builder
     */
    public Builder rlimits(Collection<RLimitSpec> rlimits) {
      if (rlimits == null) {
        this.rlimits = new ArrayList<>();
      } else {
        this.rlimits = rlimits;
      }

      return this;
    }

    /**
     * Sets the {@code uris} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param uris the {@code uris} to set
     * @return a reference to this Builder
     */
    public Builder uris(Collection<URI> uris) {
      if (uris == null) {
        this.uris = new ArrayList<>();
      } else {
        this.uris = uris;
      }

      return this;
    }

    /**
     * Adds the {@code uris} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param uri the {@code uri} to add
     * @return a reference to this Builder
     */
    public Builder addUri(URI uri) {
      this.uris.add(uri);
      return this;
    }

    /**
     * Sets the {@code tasks} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param tasks the {@code tasks} to set
     * @return a reference to this Builder
     */
    public Builder tasks(List<TaskSpec> tasks) {
      if (tasks == null) {
        this.tasks = new ArrayList<>();
      } else {
        this.tasks = tasks;
      }

      return this;
    }

    /**
     * Adds the {@code task} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param task the {@code task} to add
     * @return a reference to this Builder
     */
    public Builder addTask(TaskSpec task) {
      this.tasks.add(task);
      return this;
    }

    /**
     * Sets the {@code placementRule} and returns a reference to this Builder so that the methods can be chained
     * together.
     *
     * @param placementRule the {@code placementRule} to set
     * @return a reference to this Builder
     */
    public Builder placementRule(PlacementRule placementRule) {
      this.placementRule = placementRule;
      return this;
    }

    /**
     * Sets the {@code volumes} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param volumes the {@code volumes} to set
     * @return a reference to this Builder
     */
    public Builder volumes(Collection<VolumeSpec> volumes) {
      if (volumes == null) {
        this.volumes = new ArrayList<>();
      } else {
        this.volumes = volumes;
      }

      return this;
    }

    /**
     * Sets the {@code pre-reserved-role} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param preReservedRole the {@code preReservedRole} to set
     * @return a reference to this Builder
     */
    public Builder preReservedRole(String preReservedRole) {
      if (preReservedRole == null) {
        this.preReservedRole = Constants.ANY_ROLE;
      } else {
        this.preReservedRole = preReservedRole;
      }

      return this;
    }

    /**
     * Sets the {@code secrets} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param secrets the {@code secrets} to set
     * @return a reference to this Builder
     */
    public Builder secrets(Collection<SecretSpec> secrets) {
      if (secrets == null) {
        this.secrets = new ArrayList<>();
      } else {
        this.secrets = secrets;
      }

      return this;
    }

    /**
     * Sets whether tasks in this pod share a pid namespace and returns a reference to this Builder so that the
     * methods can be chained together.
     *
     * @param sharePidNamespace whether tasks in this pod share a pid namespace
     * @return a reference to this Builder
     */
    public Builder sharePidNamespace(Boolean sharePidNamespace) {
      this.sharePidNamespace = sharePidNamespace != null && sharePidNamespace;
      return this;
    }

    /**
     * Sets the {@code host volumes} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param hostVolumes the {@code hostVolumes} to set
     * @return a reference to this Builder
     */
    public Builder hostVolumes(Collection<HostVolumeSpec> hostVolumes) {
      if (hostVolumes == null) {
        this.hostVolumes = new ArrayList<>();
      } else {
        this.hostVolumes = hostVolumes;
      }

      return this;
    }

    /**
     * Sets the seccomp unconfined property and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param  seccompUnconfined the boolean to set
     * @return a reference to this Builder
     */
    public Builder seccompUnconfined(Boolean seccompUnconfined) {
      if (seccompUnconfined != null) {
        this.seccompUnconfined = seccompUnconfined;
      } else {
        this.seccompUnconfined = false;
      }
      return this;
    }

    /**
     * Sets the {@code seccomp profile} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param seccompProfileName the profile to set
     * @return a reference to this Builder
     */
    public Builder seccompProfileName(String seccompProfileName) {
      this.seccompProfileName = Optional.ofNullable(seccompProfileName);
      return this;
    }

    /**
     * Sets the {@code sharedMemory} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param sharedMemory the IPC Mode, PRIVATE is the only allowed value at pod level
     * @return a reference to this Builder
     */
    public Builder sharedMemory(String sharedMemory) {
      if (sharedMemory == null) {
        this.sharedMemory = Optional.empty();
        return this;
      }

      switch (sharedMemory) {
        case "PRIVATE":
          this.sharedMemory = Optional.ofNullable(Protos.LinuxInfo.IpcMode.PRIVATE);
          return this;
        case "SHARE_PARENT":
          throw new IllegalArgumentException("Cannot specify SHARE_PARENT at pod level");
        default:
          throw new IllegalArgumentException("Invalid IPC Mode");
      }
    }

    /**
     * Sets the {@code sharedMemorySize} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param sharedMemory the enum to set
     * @return a reference to this Builder
     */
    public Builder sharedMemorySize(Integer sharedMemory) {
      this.sharedMemorySize = Optional.ofNullable(sharedMemory);
      return this;
    }


    /**
     * Returns a {@code DefaultPodSpec} built from the parameters previously set.
     *
     * @return a {@code DefaultPodSpec} built with parameters of this {@code DefaultPodSpec.Builder}
     */
    public DefaultPodSpec build() {
      return new DefaultPodSpec(this);
    }
  }
}
