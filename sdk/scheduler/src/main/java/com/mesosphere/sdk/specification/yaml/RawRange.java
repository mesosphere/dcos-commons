package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML range.
 */
public final class RawRange {

  private final int begin;

  private final int end;

  private RawRange(
      @JsonProperty("begin") int begin,
      @JsonProperty("end") int end)
  {
    this.begin = begin;
    this.end = end;
  }

  public int getBegin() {
    return begin;
  }

  public int getEnd() {
    return end;
  }
}
