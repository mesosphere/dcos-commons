package com.mesosphere.sdk.offer.evaluate.placement;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Implements passthrough support for string comparisons by matching any string.
 */
public class AnyMatcher implements StringMatcher {

    public static StringMatcher create() {
        return new AnyMatcher();
    }

    @JsonCreator
    private AnyMatcher() { }

    @Override
    public boolean matches(String value) {
        return true;
    }

    @Override
    public String toString() {
        return "AnyMatcher{}";
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
