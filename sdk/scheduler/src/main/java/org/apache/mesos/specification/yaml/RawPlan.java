package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML plan.
 */
public class RawPlan {
    private String name;
    private String strategy;
    private List<RawPhase> phases;

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

    public List<RawPhase> getPhases() {
        return phases;
    }

    @JsonProperty("phases")
    public void setPhases(List<RawPhase> phases) {
        this.phases = phases;
    }
}

