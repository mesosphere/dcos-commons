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

    private RawReadinessCheck(Builder builder) {
        this(builder.cmd, builder.interval, builder.delay, builder.timeout);
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

    public static RawReadinessCheck.Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawReadinessCheck} builder.
     */
    public static class Builder {
        protected String cmd;
        protected Integer interval;
        protected Integer delay;
        protected Integer timeout;

        protected Builder() {
        }

        public Builder cmd(String cmd) {
            this.cmd = cmd;
            return this;
        }

        public Builder interval(Integer interval) {
            this.interval = interval;
            return this;
        }

        public Builder delay(Integer delay) {
            this.delay = delay;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public RawReadinessCheck build() {
            return new RawReadinessCheck(this);
        }
    }
}
