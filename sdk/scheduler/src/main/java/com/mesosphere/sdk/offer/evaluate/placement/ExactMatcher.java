package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.text.DecimalFormat;

/**
 * Implements exact string matching support for string comparisons.
 */
public final class ExactMatcher implements StringMatcher {

  private static final DecimalFormat format = new DecimalFormat("0.###");

  private String str;

  @JsonCreator
  private ExactMatcher(@JsonProperty("string") String str) {
    try {
      this.str = format.format(Double.valueOf(str));
    } catch (NumberFormatException ex) {
      this.str = str;
    }
  }

  public static StringMatcher create(String str) {
    return new ExactMatcher(str);
  }

  public static StringMatcher createAttribute(String name, String value) {
    return create(AttributeStringUtils.join(name, value));
  }

  @Override
  public boolean matches(String value) {
    String parsedValue = value;
    try {
      Double in = Double.valueOf(value);
      parsedValue = format.format(in);
    } catch (NumberFormatException ex) {
      // Continue with original value of `value`
    }
    return str.equals(parsedValue);
  }

  @JsonProperty("string")
  private String getString() {
    return str;
  }

  @Override
  public String toString() {
    return String.format("ExactMatcher{str='%s'}", str);
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }
}
