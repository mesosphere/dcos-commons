package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML replacement failure policy.
 */
public class RawReplacementFailurePolicy {

    private final Integer permanentFailureTimoutMs;
    private final Integer minReplaceDelayMs;

    private RawReplacementFailurePolicy(
            @JsonProperty("permanent-failure-timeout-ms") Integer permanentFailureTimoutMs,
            @JsonProperty("min-replace-delay-ms") Integer minReplaceDelayMs) {
        this.permanentFailureTimoutMs = permanentFailureTimoutMs;
        this.minReplaceDelayMs = minReplaceDelayMs;
    }

    public Integer getPermanentFailureTimoutMs() {
        return permanentFailureTimoutMs;
    }

    public Integer getMinReplaceDelayMs() {
        return minReplaceDelayMs;
    }
}
