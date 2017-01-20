package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Raw YAML pod.
 */
public class RawPod {

    private final String placement;
    private final Integer count;
    private final RawContainer container;
    private final String strategy;
    private final String user;
    private final Collection<String> uris;
    private final WriteOnceLinkedHashMap<String, RawTask> tasks;
    private final WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets;

    private RawPod(
            @JsonProperty("resource-sets") WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets,
            @JsonProperty("placement") String placement,
            @JsonProperty("count") Integer count,
            @JsonProperty("container") RawContainer container,
            @JsonProperty("strategy") String strategy,
            @JsonProperty("uris") Collection<String> uris,
            @JsonProperty("tasks") WriteOnceLinkedHashMap<String, RawTask> tasks,
            @JsonProperty("user") String user) {
        this.placement = placement;
        this.count = count;
        this.container = container;
        this.strategy = strategy;
        this.user = user;
        this.uris = uris;
        this.tasks = tasks;
        this.resourceSets = resourceSets;
    }

    private RawPod(Builder builder) {
        this(
                builder.resourceSets,
                builder.placement,
                builder.count,
                builder.container,
                builder.strategy,
                builder.uris,
                builder.tasks,
                builder.user);
    }

    public WriteOnceLinkedHashMap<String, RawResourceSet> getResourceSets() {
        return resourceSets;
    }

    public String getPlacement() {
        return placement;
    }

    public Integer getCount() {
        return count;
    }

    public RawContainer getContainer() {
        return container;
    }

    public String getStrategy() {
        return strategy;
    }

    public LinkedHashMap<String, RawTask> getTasks() {
        return tasks;
    }

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
    }

    public String getUser() {
        return user;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawPod} builder.
     */
    public static final class Builder {
        private String placement;
        private Integer count;
        private RawContainer container;
        private String strategy;
        private String user;
        private Collection<String> uris;
        private WriteOnceLinkedHashMap<String, RawTask> tasks;
        private WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets;

        private Builder() {
        }

        public Builder placement(String placement) {
            this.placement = placement;
            return this;
        }

        public Builder count(Integer count) {
            this.count = count;
            return this;
        }

        public Builder container(RawContainer container) {
            this.container = container;
            return this;
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder uris(Collection<String> uris) {
            this.uris = uris;
            return this;
        }

        public Builder tasks(WriteOnceLinkedHashMap<String, RawTask> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder resourceSets(WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets) {
            this.resourceSets = resourceSets;
            return this;
        }

        public RawPod build() {
            return new RawPod(this);
        }
    }
}
