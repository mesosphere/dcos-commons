package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpec {

    private final String name;
    private final String webUrl;
    private final RawScheduler scheduler;
    private final WriteOnceLinkedHashMap<String, RawPod> pods;
    private final WriteOnceLinkedHashMap<String, RawPlan> plans;

    private RawServiceSpec(
            @JsonProperty("name") String name,
            @JsonProperty("web-url") String webUrl,
            @JsonProperty("scheduler") RawScheduler scheduler,
            @JsonProperty("pods") WriteOnceLinkedHashMap<String, RawPod> pods,
            @JsonProperty("plans") WriteOnceLinkedHashMap<String, RawPlan> plans) {
        this.name = name;
        this.webUrl = webUrl;
        this.scheduler = scheduler;
        this.pods = pods;
        this.plans = plans;
    }

    public String getName() {
        return name;
    }

    public String getWebUrl() {
        return webUrl;
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
}


