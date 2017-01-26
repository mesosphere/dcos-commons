package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
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
    @Valid
    private final ContainerSpec container;
    @NotNull
    @Valid
    @Size(min = 1)
    @UniqueTaskName(message = "Task names must be unique")
    private final List<TaskSpec> tasks;
    @Valid
    private final PlacementRule placementRule;
    @Valid
    private final Collection<URI> uris;

    @JsonCreator
    public DefaultPodSpec(
            @JsonProperty("type") String type,
            @JsonProperty("user") String user,
            @JsonProperty("count") Integer count,
            @JsonProperty("container") ContainerSpec container,
            @JsonProperty("uris") Collection<URI> uris,
            @JsonProperty("task-specs") List<TaskSpec> tasks,
            @JsonProperty("placement-rule") PlacementRule placementRule) {
        this.type = type;
        this.user = user;
        this.count = count;
        this.container = container;
        this.uris = (uris != null) ? uris : Collections.emptyList();
        this.tasks = tasks;
        this.placementRule = placementRule;
    }

    private DefaultPodSpec(Builder builder) {
        this(builder.type, builder.user, builder.count, builder.container,
                builder.uris, builder.tasks, builder.placementRule);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(PodSpec copy) {
        Builder builder = new Builder();
        builder.type = copy.getType();
        builder.user = copy.getUser().isPresent() ? copy.getUser().get() : null;
        builder.count = copy.getCount();
        builder.container = copy.getContainer().isPresent() ? copy.getContainer().get() : null;
        builder.uris = copy.getUris();
        builder.tasks = new ArrayList<>();
        builder.tasks.addAll(copy.getTasks());
        builder.placementRule = copy.getPlacementRule().isPresent() ? copy.getPlacementRule().get() : null;
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
    public Optional<ContainerSpec> getContainer() {
        return Optional.ofNullable(container);
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
        private String type;
        private String user;
        private Integer count;
        private ContainerSpec container;
        private Collection<URI> uris;
        private List<TaskSpec> tasks = new ArrayList<>();
        private PlacementRule placementRule;

        private Builder() {
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
         * Sets the {@code container} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param container the {@code container} to set
         * @return a reference to this Builder
         */
        public Builder container(ContainerSpec container) {
            this.container = container;
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
         * Returns a {@code DefaultPodSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultPodSpec} built with parameters of this {@code DefaultPodSpec.Builder}
         */
        public DefaultPodSpec build() {
            DefaultPodSpec defaultPodSpec = new DefaultPodSpec(this);
            return defaultPodSpec;
        }
    }
}
