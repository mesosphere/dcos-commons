package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpec {

    private final String name;
    private final String principal;
    private final Integer apiPort;
    private final String zookeeper;
    private final WriteOnceLinkedHashMap<String, RawPod> pods;
    private final WriteOnceLinkedHashMap<String, RawPlan> plans;
    private final RawReplacementFailurePolicy replacementFailurePolicy;

    private RawServiceSpec(
            @JsonProperty("name") String name,
            @JsonProperty("principal") String principal,
            @JsonProperty("api-port") Integer apiPort,
            @JsonProperty("zookeeper") String zookeeper,
            @JsonProperty("pods") WriteOnceLinkedHashMap<String, RawPod> pods,
            @JsonProperty("plans") WriteOnceLinkedHashMap<String, RawPlan> plans,
            @JsonProperty("replacement-failure-policy") RawReplacementFailurePolicy replacementFailurePolicy) {
        this.name = name;
        this.principal = principal;
        this.apiPort = apiPort;
        this.zookeeper = zookeeper;
        this.pods = pods;
        this.plans = plans;
        this.replacementFailurePolicy = replacementFailurePolicy;
    }

    public String getName() {
        return name;
    }

    public String getPrincipal() {
        return principal;
    }

    public Integer getApiPort() {
        return apiPort;
    }

    public String getZookeeper() {
        return zookeeper;
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


