package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Raw YAML representation of Step.
 */
public class RawStep {

    private final Integer podInstance;
    private final List<String> tasks;

    private RawStep(
            @JsonProperty("pod-instance") Integer podInstance,
            @JsonProperty("tasks") List<String> tasks) {
        this.podInstance = podInstance;
        this.tasks = tasks;
    }

    public Optional<Integer> getPodInstance() {
        return Optional.ofNullable(podInstance);
    }

    public List<String> getTasks() {
        return tasks;
    }
}
