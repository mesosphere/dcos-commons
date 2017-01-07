package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpecification {

    private final String name;
    private final RawScheduler scheduler;
    private final WriteOnceLinkedHashMap<String, RawPod> pods;
    private final WriteOnceLinkedHashMap<String, RawPlan> plans;
    private final RawReplacementFailurePolicy replacementFailurePolicy;

    private RawServiceSpecification(
            @JsonProperty("name") String name,
            @JsonProperty("scheduler") RawScheduler scheduler,
            @JsonProperty("pods") WriteOnceLinkedHashMap<String, RawPod> pods,
            @JsonProperty("plans") WriteOnceLinkedHashMap<String, RawPlan> plans,
            @JsonProperty("replacement-failure-policy") RawReplacementFailurePolicy replacementFailurePolicy) {
        this.name = name;
        this.scheduler = scheduler;
        this.pods = pods;
        this.plans = plans;
        this.replacementFailurePolicy = replacementFailurePolicy;
    }

    public String getName() {
        return name;
    }

    public RawScheduler getScheduler() {
        return scheduler;
    }

    public LinkedHashMap<String, RawPod> getPods() {
        return pods;
    }

    public WriteOnceLinkedHashMap<String, RawPlan> getPlans() {
        return plans;
    }

    public RawReplacementFailurePolicy getReplacementFailurePolicy() {
        return replacementFailurePolicy;
    }
}


