package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Raw YAML representation of Step.
 */
public class RawStep {
    private Integer podInstance;
    private List<String> tasks;

    public Optional<Integer> getPodInstance() {
        return Optional.ofNullable(podInstance);
    }

    @JsonProperty("podInstance")
    public void setPodInstance(Integer podInstance) {
        this.podInstance = podInstance;
    }

    public List<String> getTasks() {
        return tasks;
    }

    @JsonProperty("tasks")
    public void setTasks(List<String> tasks) {
        this.tasks = tasks;
    }
}
