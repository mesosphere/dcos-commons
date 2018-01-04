package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.specification.validation.UniqueTaskName;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.net.URI;
import java.util.*;

/**
 * Default implementation of {@link PodSpec}.
 */
public class DefaultPodSpec implements PodSpec {
    @NotNull
    @Size(min = 1)
    private final String type;
    private final String user;
    @NotNull
    @Min(0)
    private final Integer count;
    @NotNull
    private Boolean allowDecommission;
    @Size(min = 1)
    private String image;
    @Valid
    private Collection<NetworkSpec> networks;
    @Valid
    private Collection<RLimitSpec> rlimits;
    @NotNull
    @Valid
    @Size(min = 1)
    @UniqueTaskName(message = "Task names must be unique")
    private final List<TaskSpec> tasks;
    @Valid
    private final PlacementRule placementRule;
    @Valid
    private final Collection<URI> uris;
    @Valid
    private Collection<VolumeSpec> volumes;
    @Valid
    private Collection<SecretSpec> secrets;
    private String preReservedRole;
    @NotNull
    private Boolean sharePidNamespace;

    @JsonCreator
    public DefaultPodSpec(
            @JsonProperty("type") String type,
            @JsonProperty("user") String user,
            @JsonProperty("count") Integer count,
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
            @JsonProperty("allow-decommission") Boolean allowDecommission) {
        this(
                new Builder(Optional.empty()) // Assume that Executor URI is already present
                        .type(type)
                        .user(user)
                        .count(count)
                        .image(image)
                        .networks(networks)
                        .rlimits(rlimits)
                        .uris(uris)
                        .tasks(tasks)
                        .placementRule(placementRule)
                        .volumes(volumes)
                        .preReservedRole(preReservedRole)
                        .secrets(secrets)
                        .sharePidNamespace(sharePidNamespace)
                        .allowDecommission(allowDecommission));
    }

    private DefaultPodSpec(Builder builder) {
        this.count = builder.count;
        this.allowDecommission = builder.allowDecommission;
        this.image = builder.image;
        this.networks = builder.networks;
        this.placementRule = builder.placementRule;
        this.preReservedRole = builder.preReservedRole;
        this.rlimits = builder.rlimits;
        this.secrets = builder.secrets;
        this.tasks = builder.tasks;
        this.type = builder.type;
        this.uris = builder.uris;
        this.user = builder.user;
        this.volumes = builder.volumes;
        this.sharePidNamespace = builder.sharePidNamespace;
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder(String executorUri) {
        return new Builder(Optional.of(executorUri));
    }

    public static Builder newBuilder(PodSpec copy) {
        Builder builder = new Builder(Optional.empty()); // Assume that Executor URI is already present
        builder.count = copy.getCount();
        builder.allowDecommission = copy.getAllowDecommission();
        builder.image = copy.getImage().isPresent() ? copy.getImage().get() : null;
        builder.networks = copy.getNetworks();
        builder.placementRule = copy.getPlacementRule().isPresent() ? copy.getPlacementRule().get() : null;
        builder.preReservedRole = copy.getPreReservedRole();
        builder.rlimits = copy.getRLimits();
        builder.secrets = copy.getSecrets();
        builder.tasks = copy.getTasks();
        builder.type = copy.getType();
        builder.uris = copy.getUris();
        builder.user = copy.getUser().isPresent() ? copy.getUser().get() : null;
        builder.volumes = copy.getVolumes();
        builder.sharePidNamespace = copy.getSharePidNamespace();
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
        private final Optional<String> executorUri;

        private String type;
        private String user;
        private Integer count;
        private Boolean allowDecommission = false;
        private String image;
        private PlacementRule placementRule;
        private String preReservedRole = Constants.ANY_ROLE;
        private Collection<NetworkSpec> networks = new ArrayList<>();
        private Collection<RLimitSpec> rlimits =  new ArrayList<>();
        private Collection<URI> uris = new ArrayList<>();
        private List<TaskSpec> tasks = new ArrayList<>();
        private Collection<VolumeSpec> volumes = new ArrayList<>();
        private Collection<SecretSpec> secrets = new ArrayList<>();
        private Boolean sharePidNamespace = false;

        private Builder(Optional<String> executorUri) {
            this.executorUri = executorUri;
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
         * Returns a {@code DefaultPodSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultPodSpec} built with parameters of this {@code DefaultPodSpec.Builder}
         */
        public DefaultPodSpec build() {
            if (executorUri.isPresent()) {
                // Inject the executor URI as one of the pods URIs. This ensures
                // that the scheduler properly tracks changes to executors
                // (reflected in changes to the executor URI)
                URI actualURI = URI.create(executorUri.get());
                if (this.uris == null || !this.uris.contains(actualURI)) {
                    this.addUri(actualURI);
                }
            }

            DefaultPodSpec defaultPodSpec = new DefaultPodSpec(this);
            return defaultPodSpec;
        }
    }
}
