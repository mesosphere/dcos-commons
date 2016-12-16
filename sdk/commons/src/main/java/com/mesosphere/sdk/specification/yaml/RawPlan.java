package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML plan.
 */
public class RawPlan {

    private final String strategy;
    private final WriteOnceLinkedHashMap<String, RawPhase> phases;

    private RawPlan(
            @JsonProperty("strategy") String strategy,
            @JsonProperty("phases") WriteOnceLinkedHashMap<String, RawPhase> phases) {
        this.strategy = strategy;
        this.phases = phases;
    }

    public String getStrategy() {
        return strategy;
    }

    public WriteOnceLinkedHashMap<String, RawPhase> getPhases() {
        return phases;
    }
}

