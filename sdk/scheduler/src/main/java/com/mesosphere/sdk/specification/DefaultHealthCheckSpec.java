package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Default implementation of {@link HealthCheckSpec}.
 */
public class DefaultHealthCheckSpec implements HealthCheckSpec {
    private String command;
    private Integer maxConsecutiveFailures;
    private Integer delay;
    private Integer interval;
    private Integer timeout;
    private Integer gracePeriod;

    @JsonCreator
    public DefaultHealthCheckSpec(
            @JsonProperty("command") String command,
            @JsonProperty("max-consecutive-failures") Integer maxConsecutiveFailures,
            @JsonProperty("delay") Integer delay,
            @JsonProperty("interval") Integer interval,
            @JsonProperty("timeout") Integer timeout,
            @JsonProperty("grace-period") Integer gracePeriod) {
        this.command = command;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.delay = delay;
        this.interval = interval;
        this.timeout = timeout;
        this.gracePeriod = gracePeriod;
    }

    private DefaultHealthCheckSpec(Builder builder) {
        command = builder.command;
        maxConsecutiveFailures = builder.maxConsecutiveFailures;
        delay = builder.delay;
        interval = builder.interval;
        timeout = builder.timeout;
        gracePeriod = builder.gracePeriod;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultHealthCheckSpec copy) {
        Builder builder = new Builder();
        builder.command = copy.command;
        builder.maxConsecutiveFailures = copy.maxConsecutiveFailures;
        builder.delay = copy.delay;
        builder.interval = copy.interval;
        builder.timeout = copy.timeout;
        builder.gracePeriod = copy.gracePeriod;
        return builder;
    }

    @Override
    @NotNull
    @Size(min = 1)
    public String getCommand() {
        return command;
    }

    @Override
    @JsonProperty("max-consecutive-failures")
    @Min(0)
    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    @Override
    @Min(0)
    public Integer getDelay() {
        return delay;
    }

    @Override
    @Min(0)
    public Integer getInterval() {
        return interval;
    }

    @Override
    @Min(0)
    public Integer getTimeout() {
        return timeout;
    }

    @Override
    @Min(0)
    public Integer getGracePeriod() {
        return gracePeriod;
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
     * {@code DefaultHealthCheckSpec} builder static inner class.
     */
    public static final class Builder {
        private String command;
        private Integer maxConsecutiveFailures;
        private Integer delay;
        private Integer interval;
        private Integer timeout;
        private Integer gracePeriod;

        private Builder() {
        }

        /**
         * Sets the {@code command} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param command the {@code command} to set
         * @return a reference to this Builder
         */
        public Builder command(String command) {
            this.command = command;
            return this;
        }

        /**
         * Sets the {@code maxConsecutiveFailures} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param maxConsecutiveFailures the {@code maxConsecutiveFailures} to set
         * @return a reference to this Builder
         */
        public Builder maxConsecutiveFailures(Integer maxConsecutiveFailures) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
            return this;
        }

        /**
         * Sets the {@code delay} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param delay the {@code delay} to set
         * @return a reference to this Builder
         */
        public Builder delay(Integer delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets the {@code interval} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param interval the {@code interval} to set
         * @return a reference to this Builder
         */
        public Builder interval(Integer interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Sets the {@code timeout} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param timeout the {@code timeout} to set
         * @return a reference to this Builder
         */
        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the {@code gracePeriod} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param gracePeriod the {@code gracePeriod} to set
         * @return a reference to this Builder
         */
        public Builder gracePeriod(Integer gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        /**
         * Returns a {@code DefaultHealthCheckSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultHealthCheckSpec} built with parameters of this {@code DefaultHealthCheckSpec.Builder}
         */
        public DefaultHealthCheckSpec build() {
            DefaultHealthCheckSpec defaultHealthCheckSpec = new DefaultHealthCheckSpec(this);
            ValidationUtils.validate(defaultHealthCheckSpec);
            return defaultHealthCheckSpec;
        }
    }
}
