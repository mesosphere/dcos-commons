package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Health check.
 */
public class RawHealthCheck {

    private final String cmd;
    private final Integer interval;
    private final Integer gracePeriod;
    private final Integer delay;
    private final Integer timeout;
    private final Integer maxConsecutiveFailures;

    protected RawHealthCheck(
            @JsonProperty("cmd") String cmd,
            @JsonProperty("interval") Integer intervalSecs,
            @JsonProperty("grace-period") Integer gracePeriodSecs,
            @JsonProperty("max-consecutive-failures") Integer maxConsecutiveFailures,
            @JsonProperty("delay") Integer delaySecs,
            @JsonProperty("timeout") Integer timeoutSecs) {
        this.cmd = cmd;
        this.interval = intervalSecs;
        this.gracePeriod = gracePeriodSecs;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.delay = delaySecs;
        this.timeout = timeoutSecs;
    }

    public String getCmd() {
        return cmd;
    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public Integer getDelay() {
        return delay;
    }

    public Integer getTimeout() {
        return timeout;
    }
}
