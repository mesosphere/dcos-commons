package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Representation of a port range, consisting of a begin and an end
 */
public class RangeSpec {

  private final int begin;

  private final int end;

  public RangeSpec(
      @JsonProperty("begin") int begin,
      @JsonProperty("end") int end)
  {
    this.begin = begin;
    this.end = end;
    validate();
  }

  public Integer getBegin() { return begin; }

  public Integer getEnd() { return end; }

  private void validate() {
    //TODO (kvish) validate that port range is valid
    return;
  }
}
