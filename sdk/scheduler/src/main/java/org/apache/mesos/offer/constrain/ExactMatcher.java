package org.apache.mesos.offer.constrain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implements exact string matching support for string comparisons.
 */
public class ExactMatcher implements StringMatcher {

    public static StringMatcher create(String str) {
        return new ExactMatcher(str);
    }

    private final String str;

    @JsonCreator
    private ExactMatcher(@JsonProperty("string") String str) {
        this.str = str;
    }

    @Override
    public boolean select(String value) {
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
