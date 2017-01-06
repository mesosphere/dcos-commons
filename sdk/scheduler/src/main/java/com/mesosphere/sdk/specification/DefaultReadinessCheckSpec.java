package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Default implementation of {@link ReadinessCheckSpec}.
 */
public class DefaultReadinessCheckSpec implements ReadinessCheckSpec {
    @NotNull
    private String command;

    @NotNull
    @Min(0)
    private Integer delay;

    @NotNull
    @Min(0)
    private Integer interval;

    @NotNull
    @Min(0)
    private Integer timeout;

    public DefaultReadinessCheckSpec(
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
        super();
        command = builder.command;
        delay = builder.delay;
        interval = builder.interval;
        timeout = builder.timeout;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultReadinessCheckSpec copy) {
        Builder builder = new Builder();
        builder.command = copy.command;
        builder.delay = copy.delay;
        builder.interval = copy.interval;
        builder.timeout = copy.timeout;
        return builder;
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public Integer getDelay() {
        return delay;
    }

    @Override
    public Integer getInterval() {
        return interval;
    }

    @Override
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
        private Integer delay;
        private Integer interval;
        private Integer timeout;

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
         * Returns a {@code DefaultHealthCheckSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultHealthCheckSpec} built with parameters of this {@code DefaultHealthCheckSpec.Builder}
         */
        public DefaultReadinessCheckSpec build() {
            DefaultReadinessCheckSpec defaultReadinessCheckSpec = new DefaultReadinessCheckSpec(this);
            ValidationUtils.validate(defaultReadinessCheckSpec);
            return defaultReadinessCheckSpec;
        }
    }
}
