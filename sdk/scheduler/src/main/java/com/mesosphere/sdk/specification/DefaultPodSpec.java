package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.validation.UniqueTaskName;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    @Size(min = 1)
    private String image;
    @Valid
    private Collection<NetworkSpec> networks;
    @Valid
    private Collection<RLimit> rlimits;
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

    @JsonCreator
    public DefaultPodSpec(
            @JsonProperty("type") String type,
            @JsonProperty("user") String user,
            @JsonProperty("count") Integer count,
            @JsonProperty("image") String image,
            @JsonProperty("networks") Collection<NetworkSpec> networks,
            @JsonProperty("rlimits") Collection<RLimit> rlimits,
            @JsonProperty("uris") Collection<URI> uris,
            @JsonProperty("task-specs") List<TaskSpec> tasks,
            @JsonProperty("placement-rule") PlacementRule placementRule,
            @JsonProperty("volumes") Collection<VolumeSpec> volumes) {
        this.type = type;
        this.user = user;
        this.count = count;
        this.image = image;
        this.networks = (networks != null) ? networks : Collections.emptyList();
        this.rlimits = (rlimits != null) ? rlimits : Collections.emptyList();
        this.uris = (uris != null) ? uris : Collections.emptyList();
        this.tasks = tasks;
        this.placementRule = placementRule;
        this.volumes = (volumes != null) ? volumes : Collections.emptyList();
    }

    private DefaultPodSpec(Builder builder) {
        this(builder.type, builder.user, builder.count,
             builder.image, builder.networks, builder.rlimits,
             builder.uris, builder.tasks, builder.placementRule,
             builder.volumes);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder(String executorUri) {
        return new Builder(Optional.of(executorUri));
    }

    public static Builder newBuilder(PodSpec copy) {
        Builder builder = new Builder(Optional.empty()); // Assume that Executor URI is already present
        builder.type = copy.getType();
        builder.user = copy.getUser().isPresent() ? copy.getUser().get() : null;
        builder.count = copy.getCount();
        builder.image = copy.getImage().isPresent() ? copy.getImage().get() : null;
        builder.networks = copy.getNetworks();
        builder.rlimits = copy.getRLimits();
        builder.uris = copy.getUris();
        builder.tasks = new ArrayList<>();
        builder.tasks.addAll(copy.getTasks());
        builder.placementRule = copy.getPlacementRule().isPresent() ? copy.getPlacementRule().get() : null;
        builder.volumes = copy.getVolumes();
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
    public Optional<String> getImage() {
        return Optional.ofNullable(image);
    }

    @Override
    public Collection<NetworkSpec> getNetworks() {
        return networks;
    }

    @Override
    public Collection<RLimit> getRLimits() {
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
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }


    /**
     * {@code DefaultPodSpec} builder static inner class.
     */
    public static final class Builder {
        private final Optional<String> executorUri;

        private String type;
        private String user;
        private Integer count;
        private String image;
        private Collection<NetworkSpec> networks;
        private Collection<RLimit> rlimits;
        private Collection<URI> uris;
        private List<TaskSpec> tasks = new ArrayList<>();
        private PlacementRule placementRule;
        private Collection<VolumeSpec> volumes;

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
            this.networks = networks;
            return this;
        }

        /**
         * Sets the {@code rlimits} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param rlimits the {@code rlimits} to set
         * @return a reference to this Builder
         */
        public Builder rlimits(Collection<RLimit> rlimits) {
            this.rlimits = rlimits;
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
         * Adds the {@code uris} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param uri the {@code uri} to add
         * @return a reference to this Builder
         */
        public Builder addUri(URI uri) {
            if (this.uris == null) {
                this.uris = new ArrayList<>();
            }

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
            this.tasks = tasks;
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
            this.volumes = volumes;
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
