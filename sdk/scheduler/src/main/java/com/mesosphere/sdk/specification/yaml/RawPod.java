package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Raw YAML pod.
 */
public class RawPod {
    private String name;
    private String placement;
    private Integer count;
    private RawContainer container;
    private String strategy;
    private String user;
    private WriteOnceLinkedHashMap<String, RawTask> tasks;
    private WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public WriteOnceLinkedHashMap<String, RawResourceSet> getResourceSets() {
        return resourceSets;
    }

    @JsonProperty("resource-sets")
    public void setResourceSets(WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets) {
        this.resourceSets = resourceSets;
    }

    public String getPlacement() {
        return placement;
    }

    @JsonProperty("placement")
    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public Integer getCount() {
        return count;
    }

    @JsonProperty("count")
    public void setCount(Integer count) {
        this.count = count;
    }

    public RawContainer getContainer() {
        return container;
    }

    @JsonProperty("container")
    public void setContainer(RawContainer container) {
        this.container = container;
    }

    public String getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public LinkedHashMap<String, RawTask> getTasks() {
        return tasks;
    }

    @JsonProperty("tasks")
    public void setTasks(WriteOnceLinkedHashMap<String, RawTask> tasks) {
        this.tasks = tasks;
    }

    public String getUser() {
        return user;
    }

    @JsonProperty("user")
    public void setUser(String user) {
        this.user = user;
    }
}
