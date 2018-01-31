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
    private Integer permanentFailureTimoutMin;
    @Min(0)
    private Integer minReplaceDelayMin;

    /**
     * Default time to wait between destructive task recoveries (avoid quickly making things worse).
     *
     * Default: 10 minutes
     */
    public static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN = 10;

    /**
     * Default time to wait before declaring a task as permanently failed.
     *
     * Default: 20 minutes
     */
    public static final Integer PERMANENT_FAILURE_DELAY_MIN = 20;

    public ReplacementFailurePolicy(
            @JsonProperty("permanent-failure-timeout-mins") Integer permanentFailureTimoutMin,
            @JsonProperty("min-replace-delay-mins") Integer minReplaceDelayMin) {
        this.permanentFailureTimoutMin = permanentFailureTimoutMin;
        this.minReplaceDelayMin = minReplaceDelayMin;
    }

    private ReplacementFailurePolicy(Builder builder) {
        setPermanentFailureTimoutMin(builder.permanentFailureTimoutMin);
        setMinReplaceDelayMin(builder.minReplaceDelayMin);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(ReplacementFailurePolicy copy) {
        Builder builder = new Builder();
        builder.permanentFailureTimoutMin = copy.permanentFailureTimoutMin;
        builder.minReplaceDelayMin = copy.minReplaceDelayMin;
        return builder;
    }

    public Integer getPermanentFailureTimoutMin() {
        return permanentFailureTimoutMin;
    }

    public void setPermanentFailureTimoutMin(Integer permanentFailureTimoutMins) {
        this.permanentFailureTimoutMin = permanentFailureTimoutMins;
    }

    public Integer getMinReplaceDelayMin() {
        return minReplaceDelayMin;
    }

    public void setMinReplaceDelayMin(Integer minReplaceDelayMin) {
        this.minReplaceDelayMin = minReplaceDelayMin;
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
            this.permanentFailureTimoutMin = PERMANENT_FAILURE_DELAY_MIN;
            this.minReplaceDelayMin = DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN;
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
            ReplacementFailurePolicy replacementFailurePolicy = new ReplacementFailurePolicy(this);
            ValidationUtils.validate(replacementFailurePolicy);
            return replacementFailurePolicy;
        }
    }
}
