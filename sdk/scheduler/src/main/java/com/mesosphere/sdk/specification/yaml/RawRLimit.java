package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML individual rlimit specification.
 */
public final class RawRLimit {
  private final Long soft;

  private final Long hard;

  @JsonCreator
  private RawRLimit(@JsonProperty("soft") Long soft, @JsonProperty("hard") Long hard) {
    this.soft = soft;
    this.hard = hard;
  }

  public Long getSoft() {
    return soft;
  }

  public Long getHard() {
    return hard;
  }
}
