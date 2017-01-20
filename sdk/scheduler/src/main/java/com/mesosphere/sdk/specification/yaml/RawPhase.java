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
    private final List<String> tasks;

    private RawPhase(
            @JsonProperty("strategy") String strategy,
            @JsonProperty("steps") List<WriteOnceLinkedHashMap<Integer, List<String>>> steps,
            @JsonProperty("pod") String pod,
            @JsonProperty("tasks") List<String> tasks) {
        this.strategy = strategy;
        this.steps = steps;
        this.pod = pod;
        this.tasks = tasks;
    }

    private RawPhase(Builder builder) {
        this(
                builder.strategy,
                builder.steps,
                builder.pod,
                builder.tasks);
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

    public List<String> getTasks() {
        return tasks;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawPhase} builder.
     */
    public static final class Builder {
        private String strategy;
        private String pod;
        private List<WriteOnceLinkedHashMap<Integer, List<String>>> steps;
        private List<String> tasks;

        private Builder() {
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder pod(String pod) {
            this.pod = pod;
            return this;
        }

        public Builder scheduler(List<WriteOnceLinkedHashMap<Integer, List<String>>> steps) {
            this.steps = steps;
            return this;
        }

        public Builder tasks(List<String> tasks) {
            this.tasks = tasks;
            return this;
        }

        public RawPhase build() {
            return new RawPhase(this);
        }
    }
}

