package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML phase.
 */
public class RawPhase {

    private String name;
    private final String strategy;
    private final String pod;
    private final List<RawStep> steps;

    private RawPhase(
            @JsonProperty("strategy") String strategy,
            @JsonProperty("steps") List<RawStep> steps,
            @JsonProperty("pod") String pod) {
        this.name = null;
        this.strategy = strategy;
        this.steps = steps;
        this.pod = pod;
    }

    @JsonIgnore
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<RawStep> getSteps() {
        return steps;
    }

    public String getPod() {
        return pod;
    }
}

