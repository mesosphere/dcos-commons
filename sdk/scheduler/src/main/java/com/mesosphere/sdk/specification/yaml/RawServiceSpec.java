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

    private RawServiceSpec(Builder builder) {
       this(
               builder.name,
               builder.webUrl,
               builder.scheduler,
               builder.pods,
               builder.plans);
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

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawServiceSpec} builder.
     */
    public static final class Builder {
        private String name;
        private String webUrl;
        private RawScheduler scheduler;
        private WriteOnceLinkedHashMap<String, RawPod> pods;
        private WriteOnceLinkedHashMap<String, RawPlan> plans;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder webUrl(String webUrl) {
            this.webUrl = webUrl;
            return this;
        }

        public Builder scheduler(RawScheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder pods(WriteOnceLinkedHashMap<String, RawPod> pods) {
            this.pods = pods;
            return this;
        }

        public Builder plans(WriteOnceLinkedHashMap<String, RawPlan> plans) {
            this.plans = plans;
            return this;
        }

        public RawServiceSpec build() {
            return new RawServiceSpec(this);
        }
    }
}


