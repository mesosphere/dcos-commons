package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML phase.
 */
public class RawPhase {
    private String name;
    private String strategy;
    private String pod;
    private List<RawStep> steps;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<RawStep> getSteps() {
        return steps;
    }

    @JsonProperty("steps")
    public void setSteps(List<RawStep> steps) {
        this.steps = steps;
    }

    public String getPod() {
        return pod;
    }

    @JsonProperty("pod")
    public void setPod(String pod) {
        this.pod = pod;
    }
}

