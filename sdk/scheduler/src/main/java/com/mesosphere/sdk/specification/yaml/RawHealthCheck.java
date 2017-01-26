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

    private RawHealthCheck(Builder builder) {
        this(
                builder.cmd,
                builder.interval,
                builder.gracePeriodSecs,
                builder.maxConsecutiveFailures,
                builder.delay,
                builder.timeout);
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public static RawHealthCheck.Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawHealthCheck} builder.
     */
    public static final class Builder extends RawReadinessCheck.Builder {
        private Integer gracePeriodSecs;
        private Integer maxConsecutiveFailures;

        private Builder() {
            super();
        }

        public Builder gracePeriodsSecs(Integer gracePeriodSecs) {
            this.gracePeriodSecs = gracePeriodSecs;
            return this;
        }

        public Builder maxConsecutiveFailures(Integer maxConsecutiveFailures) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
            return this;
        }

        public RawHealthCheck build() {
            return new RawHealthCheck(this);
        }
    }
}
