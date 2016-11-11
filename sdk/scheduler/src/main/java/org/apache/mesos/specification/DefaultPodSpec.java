package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class DefaultPodSpec implements PodSpec {
    private String type;
    private String user;
    private int count;
    private List<TaskSpec> tasks;
    private PlacementRule placementRule;
    private Collection<ResourceSet> resources;

    @JsonCreator
    public DefaultPodSpec(
            @JsonProperty("type") String type,
            @JsonProperty("user") String user,
            @JsonProperty("count") int count,
            @JsonProperty("task_specs") List<TaskSpec> tasks,
            @JsonProperty("placement_rule") PlacementRule placementRule,
            @JsonProperty("resource_sets") Collection<ResourceSet> resources) {
        this.type = type;
        this.user = user;
        this.count = count;
        this.tasks = tasks;
        this.placementRule = placementRule;
        this.resources = resources;
    }

    private DefaultPodSpec(Builder builder) {
        this(builder.type, builder.user, builder.count, builder.tasks, builder.placementRule, builder.resources);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultPodSpec copy) {
        Builder builder = new Builder();
        builder.type = copy.type;
        builder.user = copy.user;
        builder.count = copy.count;
        builder.tasks = copy.tasks;
        builder.placementRule = copy.placementRule;
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
    public List<TaskSpec> getTasks() {
        return tasks;
    }

    @Override
    public Collection<ResourceSet> getResources() {
        return resources;
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
        private int count;
        private List<TaskSpec> tasks;
        private PlacementRule placementRule;
        private Collection<ResourceSet> resources;

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
        public Builder count(int count) {
            this.count = count;
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
         * Sets the {@code placementRule} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param placementRule the {@code placementRule} to set
         * @return a reference to this Builder
         */
        public Builder placementRule(PlacementRule placementRule) {
            this.placementRule = placementRule;
            return this;
        }

        public Builder resources(Collection<ResourceSet> resources) {
            this.resources = resources;
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
