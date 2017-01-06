package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.constraints.Min;

/**
 * Destructive Replacement Failure Policy configuration.
 */
public class ReplacementFailurePolicy {
    @Min(0)
    private Integer permanentFailureTimoutMins;
    @Min(0)
    private Integer minReplaceDelayMins;

    public ReplacementFailurePolicy(
            @JsonProperty("permanent-failure-timeout-mins") Integer permanentFailureTimoutMins,
            @JsonProperty("min-replace-delay-mins") Integer minReplaceDelayMins) {
        this.permanentFailureTimoutMins = permanentFailureTimoutMins;
        this.minReplaceDelayMins = minReplaceDelayMins;
    }

    private ReplacementFailurePolicy(Builder builder) {
        setPermanentFailureTimoutMins(builder.permanentFailureTimoutMs);
        setMinReplaceDelayMins(builder.minReplaceDelayMs);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(ReplacementFailurePolicy copy) {
        Builder builder = new Builder();
        builder.permanentFailureTimoutMs = copy.permanentFailureTimoutMins;
        builder.minReplaceDelayMs = copy.minReplaceDelayMins;
        return builder;
    }

    public Integer getPermanentFailureTimoutMins() {
        return permanentFailureTimoutMins;
    }

    public void setPermanentFailureTimoutMins(Integer permanentFailureTimoutMins) {
        this.permanentFailureTimoutMins = permanentFailureTimoutMins;
    }

    public Integer getMinReplaceDelayMins() {
        return minReplaceDelayMins;
    }

    public void setMinReplaceDelayMins(Integer minReplaceDelayMins) {
        this.minReplaceDelayMins = minReplaceDelayMins;
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
        private Integer permanentFailureTimoutMs;
        private Integer minReplaceDelayMs;

        private Builder() {
        }

        /**
         * Sets the {@code permanentFailureTimoutMins} and returns a reference to this Builder so that the methods can
         * be chained together.
         *
         * @param permanentFailureTimoutMs the {@code permanentFailureTimoutMins} to set
         * @return a reference to this Builder
         */
        public Builder permanentFailureTimoutMs(Integer permanentFailureTimoutMs) {
            this.permanentFailureTimoutMs = permanentFailureTimoutMs;
            return this;
        }

        /**
         * Sets the {@code minReplaceDelayMins} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param minReplaceDelayMs the {@code minReplaceDelayMins} to set
         * @return a reference to this Builder
         */
        public Builder minReplaceDelayMs(Integer minReplaceDelayMs) {
            this.minReplaceDelayMs = minReplaceDelayMs;
            return this;
        }

        /**
         * Returns a {@code ReplacementFailurePolicy} built from the parameters previously set.
         *
         * @return a {@code ReplacementFailurePolicy} built with parameters of this
         * {@code ReplacementFailurePolicy.Builder}
         */
        public ReplacementFailurePolicy build() {
            ReplacementFailurePolicy replacementFailurePolicy = new ReplacementFailurePolicy(this);
            ValidationUtils.validate(replacementFailurePolicy);
            return replacementFailurePolicy;
        }
    }
}
