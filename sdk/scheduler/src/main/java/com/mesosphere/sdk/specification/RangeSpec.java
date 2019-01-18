package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * Representation of a port range, consisting of a begin and an end. The range is inclusive of begin
 * and end values. If either begin or empty is left empty the range is unbounded on that side.
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
    this.end = (end == null || end == 0) ? MAX_PORT : end;
    validate();
  }

  public Integer getBegin() {
    return begin;
  }

  public Integer getEnd() {
    return end;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  private void validate() {
    if (begin > end) {
      throw new IllegalArgumentException("invalid port constraint");
    }
  }
}
