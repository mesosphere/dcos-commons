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
}
