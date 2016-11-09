package org.apache.mesos.specification;

import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class DefaultPodSpec implements PodSpec {
    private String type;
    private String user;
    private int count;
    private List<TaskSpec> tasks;
    private Collection<ResourceSet> resources;
    private PlacementRule placementRule;

    DefaultPodSpec(String type,
                   String user,
                   int count,
                   List<TaskSpec> tasks,
                   Collection<ResourceSet> resources,
                   PlacementRule placementRule) {
        this.type = type;
        this.user = user;
        this.count = count;
        this.tasks = tasks;
        this.resources = resources;
        this.placementRule = placementRule;
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
    public Integer getCount() {
        return count;
    }

    public static class Builder {
        private String type;
        private String user;
        private Integer count;
        private List<TaskSpec> tasks;
        private Collection<ResourceSet> resources;
        private PlacementRule placementRule;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder count(Integer count) {
            this.count = count;
            return this;
        }

        public Builder tasks(List<TaskSpec> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder resources(Collection<ResourceSet> resources) {
            this.resources = resources;
            return this;
        }

        public Builder placementRule(PlacementRule placementRule) {
            this.placementRule = placementRule;
            return this;
        }

        public DefaultPodSpec build() {
            return new DefaultPodSpec(type, user, count, tasks, resources, placementRule);
        }
    }
}
