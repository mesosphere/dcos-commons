package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Health check.
 */
public class RawHealthCheck extends RawReadinessCheck {

    private final Integer gracePeriod;
    private final Integer maxConsecutiveFailures;

    private RawHealthCheck(
            @JsonProperty("cmd") String cmd,
            @JsonProperty("interval") Integer intervalSecs,
            @JsonProperty("grace-period") Integer gracePeriodSecs,
            @JsonProperty("max-consecutive-failures") Integer maxConsecutiveFailures,
            @JsonProperty("delay") Integer delaySecs,
            @JsonProperty("timeout") Integer timeoutSecs) {
        super(cmd, intervalSecs, delaySecs, timeoutSecs);
        this.gracePeriod = gracePeriodSecs;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }
}
