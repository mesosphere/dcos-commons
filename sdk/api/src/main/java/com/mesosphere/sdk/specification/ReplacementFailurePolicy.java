package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Destructive Replacement Failure Policy configuration.
 */
public interface ReplacementFailurePolicy {
    /**
     * The minimum period, in minutes, until a stopped task is considered permanently failed.
     */
    @JsonProperty("permanent-failure-timeout-mins")
    Integer getPermanentFailureTimeoutMins();

    /**
     * The minimum service-wide delay, in minutes, between destructive launches.
     */
    @JsonProperty("min-replace-delay-mins")
    Integer getMinReplaceDelayMins();
}
