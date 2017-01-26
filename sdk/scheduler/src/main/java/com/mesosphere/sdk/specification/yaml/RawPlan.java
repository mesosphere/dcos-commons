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

    private RawPlan(Builder builder) {
        this(builder.strategy, builder.phases);
    }

    public String getStrategy() {
        return strategy;
    }

    public WriteOnceLinkedHashMap<String, RawPhase> getPhases() {
        return phases;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawPlan} builder.
     */
    public static final class Builder {
        private String strategy;
        private WriteOnceLinkedHashMap<String, RawPhase> phases;

        private Builder() {
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder dest(WriteOnceLinkedHashMap<String, RawPhase> phases) {
            this.phases = phases;
            return this;
        }

        public RawPlan build() {
            return new RawPlan(this);
        }
    }
}

