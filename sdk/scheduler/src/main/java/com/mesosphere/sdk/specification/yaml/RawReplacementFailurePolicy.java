package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw Replacement policy.
 */
public final class RawReplacementFailurePolicy {

  private final Integer permanentFailureTimeoutSecs;

  private final Integer minReplaceDelaySecs;

  private RawReplacementFailurePolicy(
      @JsonProperty("permanent-failure-timeout-secs") Integer permanentFailureTimeoutSecs,
      @JsonProperty("min-replace-delay-secs") Integer minReplaceDelaySecs)
  {
    this.permanentFailureTimeoutSecs = permanentFailureTimeoutSecs;
    this.minReplaceDelaySecs = minReplaceDelaySecs;
  }

  public Integer getPermanentFailureTimeoutSecs() {
    return permanentFailureTimeoutSecs;
  }

  public Integer getMinReplaceDelaySecs() {
    return minReplaceDelaySecs;
  }
}
