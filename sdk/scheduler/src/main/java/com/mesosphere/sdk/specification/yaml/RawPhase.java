package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML phase.
 */
public class RawPhase {

    private final String strategy;
    private final String pod;
    private final List<WriteOnceLinkedHashMap<Integer, List<String>>> steps;
    private final List<List<String>> tasks;

    private RawPhase(
            @JsonProperty("strategy") String strategy,
            @JsonProperty("steps") List<WriteOnceLinkedHashMap<Integer, List<String>>> steps,
            @JsonProperty("pod") String pod,
            @JsonProperty("tasks") List<List<String>> tasks) {
        this.strategy = strategy;
        this.steps = steps;
        this.pod = pod;
        this.tasks = tasks;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<WriteOnceLinkedHashMap<Integer, List<String>>> getSteps() {
        return steps;
    }

    public String getPod() {
        return pod;
    }

    public List<List<String>> getTasks() {
        return tasks;
    }
}

