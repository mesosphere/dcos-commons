package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Default implementation of {@link ReadinessCheckSpec}.
 */
public class DefaultReadinessCheckSpec implements ReadinessCheckSpec {

    private final String command;
    private final Integer delay;
    private final Integer interval;
    private final Integer timeout;

    private DefaultReadinessCheckSpec(
            @JsonProperty("command") String command,
            @JsonProperty("delay") Integer delay,
            @JsonProperty("interval") Integer interval,
            @JsonProperty("timeout") Integer timeout) {
        this.command = command;
        this.delay = delay;
        this.interval = interval;
        this.timeout = timeout;
    }

    private DefaultReadinessCheckSpec(Builder builder) {
        this(builder.command, builder.delay, builder.interval, builder.timeout);

        ValidationUtils.nonNull(this, "command", command);
        ValidationUtils.nonNegative(this, "delay", delay);
        ValidationUtils.nonNegative(this, "interval", interval);
        ValidationUtils.nonNegative(this, "timeout", timeout);
    }

    public static Builder newBuilder(String command, int interval, int timeout) {
        return new Builder(command, interval, timeout);
    }

    public static Builder newBuilder(DefaultReadinessCheckSpec copy) {
        return new Builder(copy.command, copy.interval, copy.timeout)
                .delay(copy.delay);
    }

    @Override
    @JsonProperty("command")
    public String getCommand() {
        return command;
    }

    @Override
    @JsonProperty("delay")
    public Integer getDelay() {
        return delay;
    }

    @Override
    @JsonProperty("interval")
    public Integer getInterval() {
        return interval;
    }

    @Override
    @JsonProperty("timeout")
    public Integer getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * {@code DefaultReadinessCheckSpec} builder static inner class.
     */
    public static final class Builder {
        private String command;
        private int delay;
        private int interval;
        private int timeout;

        private Builder(String command, int interval, int timeout) {
            this.command = command;
            this.delay = 0;
            this.interval = interval;
            this.timeout = timeout;
        }

        /**
         * Sets the {@code delay} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param delay the {@code delay} to set
         * @return a reference to this Builder
         */
        public Builder delay(int delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Returns a {@code DefaultHealthCheckSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultHealthCheckSpec} built with parameters of this {@code DefaultHealthCheckSpec.Builder}
         */
        public DefaultReadinessCheckSpec build() {
            return new DefaultReadinessCheckSpec(this);
        }
    }
}
