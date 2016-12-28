package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML phase.
 */
public class RawPhase {

    private final String strategy;
    private final String pod;
    private final List<RawStep> steps;

    private RawPhase(
            @JsonProperty("strategy") String strategy,
            @JsonProperty("steps") List<RawStep> steps,
            @JsonProperty("pod") String pod) {
        this.strategy = strategy;
        this.steps = steps;
        this.pod = pod;
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

