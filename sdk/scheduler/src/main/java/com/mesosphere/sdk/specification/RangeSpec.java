package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Representation of a port range, consisting of a begin and an end.
 */
public class RangeSpec {

  public static final int MIN_PORT = 0;

  public static final int MAX_PORT = 65535;

  private final int begin;

  private final int end;

  public RangeSpec(
      @JsonProperty("begin") Integer begin,
      @JsonProperty("end") Integer end)
  {
    this.begin = begin == null ? MIN_PORT : begin;
    this.end = end == null ? MAX_PORT : end;
    validate();
  }

  public Integer getBegin() {
    return begin;
  }

  public Integer getEnd() {
    return end;
  }

  private void validate() {
    if (begin > end) {
      throw new IllegalArgumentException("invalid port constraint");
    }
  }
}
