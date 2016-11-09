package org.apache.mesos.specification;

import java.time.Duration;

/**
 * Default implementation of {@link HealthCheckSpec}.
 */
public class DefaultHealthCheckSpec implements HealthCheckSpec {
    private String command;
    private Integer maxConsecutiveFailures;
    private Duration delay;
    private Duration interval;
    private Duration timeout;
    private Duration gracePeriod;

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
    public String getCommand() {
        return command;
    }

    @Override
    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    @Override
    public Duration getDelay() {
        return delay;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public Duration getGracePeriod() {
        return gracePeriod;
    }


    /**
     * {@code DefaultHealthCheckSpec} builder static inner class.
     */
    public static final class Builder {
        private String command;
        private Integer maxConsecutiveFailures;
        private Duration delay;
        private Duration interval;
        private Duration timeout;
        private Duration gracePeriod;

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
         * Sets the {@code maxConsecutiveFailures} and returns a reference to this Builder so that the methods can be chained together.
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
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets the {@code interval} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param interval the {@code interval} to set
         * @return a reference to this Builder
         */
        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Sets the {@code timeout} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param timeout the {@code timeout} to set
         * @return a reference to this Builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the {@code gracePeriod} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param gracePeriod the {@code gracePeriod} to set
         * @return a reference to this Builder
         */
        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        /**
         * Returns a {@code DefaultHealthCheckSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultHealthCheckSpec} built with parameters of this {@code DefaultHealthCheckSpec.Builder}
         */
        public DefaultHealthCheckSpec build() {
            return new DefaultHealthCheckSpec(this);
        }
    }
}
