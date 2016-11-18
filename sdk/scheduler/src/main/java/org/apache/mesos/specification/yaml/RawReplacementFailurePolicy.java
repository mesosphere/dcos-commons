package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML replacement failure policy.
 */
public class RawReplacementFailurePolicy {
    private Integer permanentFailureTimoutMs;
    private Integer minReplaceDelayMs;

    public Integer getPermanentFailureTimoutMs() {
        return permanentFailureTimoutMs;
    }

    @JsonProperty("permanent-failure-timeout-ms")
    public void setPermanentFailureTimoutMs(Integer permanentFailureTimoutMs) {
        this.permanentFailureTimoutMs = permanentFailureTimoutMs;
    }

    public Integer getMinReplaceDelayMs() {
        return minReplaceDelayMs;
    }

    @JsonProperty("min-replace-delay-ms")
    public void setMinReplaceDelayMs(Integer minReplaceDelayMs) {
        this.minReplaceDelayMs = minReplaceDelayMs;
    }
}
