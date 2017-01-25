package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Readiness check.
 */
public class RawReadinessCheck {
    private final String cmd;
    private final Integer interval;
    private final Integer delay;
    private final Integer timeout;

    protected RawReadinessCheck(
            @JsonProperty("cmd") String cmd,
            @JsonProperty("interval") Integer intervalSecs,
            @JsonProperty("delay") Integer delaySecs,
            @JsonProperty("timeout") Integer timeoutSecs) {
        this.cmd = cmd;
        this.interval = intervalSecs;
        this.delay = delaySecs;
        this.timeout = timeoutSecs;
    }

    public String getCmd() {
        return cmd;
    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getDelay() {
        return delay;
    }

    public Integer getTimeout() {
        return timeout;
    }
}
