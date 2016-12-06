package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Health check.
 */
public class RawHealthCheck {
    String name;
    String cmd;
    Integer interval;
    Integer gracePeriod;
    Integer delay;
    Integer timeout;
    Integer maxConsecutiveFailures;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getCmd() {
        return cmd;
    }

    @JsonProperty("cmd")
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public Integer getInterval() {
        return interval;
    }

    @JsonProperty("interval")
    public void setInterval(Integer intervalSecs) {
        this.interval = intervalSecs;
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    @JsonProperty("grace-period")
    public void setGracePeriod(Integer gracePeriodSecs) {
        this.gracePeriod = gracePeriodSecs;
    }

    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    @JsonProperty("max-consecutive-failures")
    public void setMaxConsecutiveFailures(Integer maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public Integer getDelay() {
        return delay;
    }

    @JsonProperty("delay")
    public void setDelay(int delaySecs) {
        this.delay = delaySecs;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @JsonProperty("timeout")
    public void setTimeout(Integer timeoutSecs) {
        this.timeout = timeoutSecs;
    }
}
