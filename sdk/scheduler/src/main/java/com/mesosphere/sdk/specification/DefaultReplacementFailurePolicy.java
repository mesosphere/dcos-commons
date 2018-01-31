package com.mesosphere.sdk.specification;

import javax.validation.constraints.Min;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

/**
 * Default implementation of {@link ReplacementFailurePolicy}.
 */
@JsonTypeName("ReplacementFailurePolicy")
public class DefaultReplacementFailurePolicy implements ReplacementFailurePolicy {
    @Min(0)
    private Integer permanentFailureTimeoutMin;
    @Min(0)
    private Integer minReplaceDelayMin;

    /**
     * Default time to wait between destructive task recoveries (avoid quickly making things worse).
     *
     * Default: 10 minutes
     */
    private static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN = 10;

    /**
     * Default time to wait before declaring a task as permanently failed.
     *
     * Default: 20 minutes
     */
    private static final Integer PERMANENT_FAILURE_DELAY_MIN = 20;

    @JsonCreator
    public DefaultReplacementFailurePolicy(
            @JsonProperty("permanent-failure-timeout-mins") Integer permanentFailureTimeoutMin,
            @JsonProperty("min-replace-delay-mins") Integer minReplaceDelayMin) {
        this.permanentFailureTimeoutMin = permanentFailureTimeoutMin != null
                ? permanentFailureTimeoutMin : PERMANENT_FAILURE_DELAY_MIN;
        this.minReplaceDelayMin = minReplaceDelayMin != null
                ? minReplaceDelayMin : DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MIN;
        ValidationUtils.validate(this);
    }

    @Override
    public Integer getPermanentFailureTimeoutMins() {
        return permanentFailureTimeoutMin;
    }

    @Override
    public Integer getMinReplaceDelayMins() {
        return minReplaceDelayMin;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
