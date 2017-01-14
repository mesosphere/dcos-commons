package com.mesosphere.sdk.offer.evaluate.placement;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.mesosphere.sdk.offer.AttributeStringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implements exact string matching support for string comparisons.
 */
public class ExactMatcher implements StringMatcher {

    public static StringMatcher create(String str) {
        return new ExactMatcher(str);
    }

    public static StringMatcher createAttribute(String name, String value) {
        return create(AttributeStringUtils.join(name, value));
    }

    private final String str;

    @JsonCreator
    private ExactMatcher(@JsonProperty("string") String str) {
        this.str = str;
    }

    @Override
    public boolean matches(String value) {
        return str.equals(value);
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
