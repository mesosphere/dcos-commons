package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.specification.validation.ValidationUtils;

import javax.validation.constraints.Min;

/**
 * Destructive Replacement Failure Policy configuration.
 */
public class ReplacementFailurePolicy {
    @Min(0)
    private Integer permanentFailureTimoutMs;
    @Min(0)
    private Integer minReplaceDelayMs;

    public ReplacementFailurePolicy(
            @JsonProperty("permanent_failure_timeout_ms") Integer permanentFailureTimoutMs,
            @JsonProperty("min_replace_delay_ms") Integer minReplaceDelayMs) {
        this.permanentFailureTimoutMs = permanentFailureTimoutMs;
        this.minReplaceDelayMs = minReplaceDelayMs;
    }

    private ReplacementFailurePolicy(Builder builder) {
        setPermanentFailureTimoutMs(builder.permanentFailureTimoutMs);
        setMinReplaceDelayMs(builder.minReplaceDelayMs);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(ReplacementFailurePolicy copy) {
        Builder builder = new Builder();
        builder.permanentFailureTimoutMs = copy.permanentFailureTimoutMs;
        builder.minReplaceDelayMs = copy.minReplaceDelayMs;
        return builder;
    }

    public Integer getPermanentFailureTimoutMs() {
        return permanentFailureTimoutMs;
    }

    public void setPermanentFailureTimoutMs(Integer permanentFailureTimoutMs) {
        this.permanentFailureTimoutMs = permanentFailureTimoutMs;
    }

    public Integer getMinReplaceDelayMs() {
        return minReplaceDelayMs;
    }

    public void setMinReplaceDelayMs(Integer minReplaceDelayMs) {
        this.minReplaceDelayMs = minReplaceDelayMs;
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
         * Sets the {@code permanentFailureTimoutMs} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param permanentFailureTimoutMs the {@code permanentFailureTimoutMs} to set
         * @return a reference to this Builder
         */
        public Builder permanentFailureTimoutMs(Integer permanentFailureTimoutMs) {
            this.permanentFailureTimoutMs = permanentFailureTimoutMs;
            return this;
        }

        /**
         * Sets the {@code minReplaceDelayMs} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param minReplaceDelayMs the {@code minReplaceDelayMs} to set
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
