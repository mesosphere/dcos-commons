package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML plan.
 */
public class RawPlan {
    private String name;
    private String strategy;
    private WriteOnceLinkedHashMap<String, RawPhase> phases;

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

    public WriteOnceLinkedHashMap<String, RawPhase> getPhases() {
        return phases;
    }

    @JsonProperty("phases")
    public void setPhases(WriteOnceLinkedHashMap<String, RawPhase> phases) {
        this.phases = phases;
    }
}

