package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpecification {
    private String name;
    private String principal;
    private Integer apiPort;
    private String zookeeper;
    private WriteOnceLinkedHashMap<String, RawPod> pods;
    private WriteOnceLinkedHashMap<String, RawPlan> plans;
    private RawReplacementFailurePolicy replacementFailurePolicy;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getPrincipal() {
        return principal;
    }

    @JsonProperty("principal")
    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public Integer getApiPort() {
        return apiPort;
    }

    @JsonProperty("api-port")
    public void setApiPort(Integer apiPort) {
        this.apiPort = apiPort;
    }

    public String getZookeeper() {
        return zookeeper;
    }

    @JsonProperty("zookeeper")
    public void setZookeeper(String zookeeper) {
        this.zookeeper = zookeeper;
    }

    public LinkedHashMap<String, RawPod> getPods() {
        return pods;
    }

    @JsonProperty("pods")
    public void setPods(WriteOnceLinkedHashMap<String, RawPod> pods) {
        this.pods = pods;
    }

    public WriteOnceLinkedHashMap<String, RawPlan> getPlans() {
        return plans;
    }

    @JsonProperty("plans")
    public void setPlans(WriteOnceLinkedHashMap<String, RawPlan> plans) {
        this.plans = plans;
    }

    public RawReplacementFailurePolicy getReplacementFailurePolicy() {
        return replacementFailurePolicy;
    }

    @JsonProperty("replacement-failure-policy")
    public void setReplacementFailurePolicy(RawReplacementFailurePolicy replacementFailurePolicy) {
        this.replacementFailurePolicy = replacementFailurePolicy;
    }
}


