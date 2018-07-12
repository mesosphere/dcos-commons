package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Destructive Replacement Failure Policy configuration.
 */
public class ReplacementFailurePolicy {

    private final Integer permanentFailureTimeoutMins;
    private final Integer minReplaceDelayMins;

    /**
     * Default time to wait between destructive task recoveries (avoid quickly making things worse).
     *
     * Default: 10 minutes
     */
    public static final Integer DEFAULT_DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN = 10;

    /**
     * Default time to wait before declaring a task as permanently failed.
     *
     * Default: 20 minutes
     */
    private static final Integer DEFAULT_PERMANENT_FAILURE_DELAY_MINS = 20;

    private ReplacementFailurePolicy(
            @JsonProperty("permanent-failure-timeout-mins") Integer permanentFailureTimeoutMins,
            @JsonProperty("min-replace-delay-mins") Integer minReplaceDelayMins) {
        this.permanentFailureTimeoutMins = permanentFailureTimeoutMins;
        this.minReplaceDelayMins = minReplaceDelayMins;
    }

    private ReplacementFailurePolicy(Builder builder) {
        this(builder.permanentFailureTimoutMin, builder.minReplaceDelayMin);
        ValidationUtils.nonNegative(this, "permanentFailureTimeoutMins", permanentFailureTimeoutMins);
        ValidationUtils.nonNegative(this, "minReplaceDelayMins", minReplaceDelayMins);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(ReplacementFailurePolicy copy) {
        Builder builder = new Builder();
        builder.permanentFailureTimoutMin = copy.permanentFailureTimeoutMins;
        builder.minReplaceDelayMin = copy.minReplaceDelayMins;
        return builder;
    }

    @JsonProperty("permanent-failure-timeout-mins")
    public Integer getPermanentFailureTimeoutMins() {
        return permanentFailureTimeoutMins;
    }

    @JsonProperty("min-replace-delay-mins")
    public Integer getMinReplaceDelayMins() {
        return minReplaceDelayMins;
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
     * {@code ReplacementFailurePolicy} builder static inner class.
     */
    public static final class Builder {
        private Integer permanentFailureTimoutMin;
        private Integer minReplaceDelayMin;

        private Builder() {
            this.permanentFailureTimoutMin = DEFAULT_PERMANENT_FAILURE_DELAY_MINS;
            this.minReplaceDelayMin = DEFAULT_DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN;
        }

        /**
         * Sets the {@code permanentFailureTimoutMin} and returns a reference to this Builder so that the methods can
         * be chained together.
         *
         * @param permanentFailureTimoutMin the {@code permanentFailureTimoutMin} to set
         * @return a reference to this Builder
         */
        public Builder permanentFailureTimoutMs(Integer permanentFailureTimoutMin) {
            this.permanentFailureTimoutMin = permanentFailureTimoutMin;
            return this;
        }

        /**
         * Sets the {@code minReplaceDelayMin} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param minReplaceDelayMin the {@code minReplaceDelayMin} to set
         * @return a reference to this Builder
         */
        public Builder minReplaceDelayMs(Integer minReplaceDelayMin) {
            this.minReplaceDelayMin = minReplaceDelayMin;
            return this;
        }

        /**
         * Returns a {@code ReplacementFailurePolicy} built from the parameters previously set.
         *
         * @return a {@code ReplacementFailurePolicy} built with parameters of this
         * {@code ReplacementFailurePolicy.Builder}
         */
        public ReplacementFailurePolicy build() {
            return new ReplacementFailurePolicy(this);
        }
    }
}
