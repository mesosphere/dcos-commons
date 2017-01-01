package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Readiness check.
 */
public class RawReadinessCheck extends RawHealthCheck {
    private RawReadinessCheck(
            @JsonProperty("cmd") String cmd,
            @JsonProperty("interval") Integer intervalSecs,
            @JsonProperty("grace-period") Integer gracePeriodSecs,
            @JsonProperty("max-consecutive-failures") Integer maxConsecutiveFailures,
            @JsonProperty("delay") Integer delaySecs,
            @JsonProperty("timeout") Integer timeoutSecs) {
        super(cmd, intervalSecs, gracePeriodSecs, maxConsecutiveFailures, delaySecs, timeoutSecs);
    }
}
