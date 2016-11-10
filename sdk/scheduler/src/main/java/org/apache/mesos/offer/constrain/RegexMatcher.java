package org.apache.mesos.offer.constrain;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implements fuzzy regular expression support for string comparisons.
 */
public class RegexMatcher implements StringMatcher {

    public static StringMatcher create(String pattern) {
        return new RegexMatcher(pattern);
    }

    private final Pattern pattern;

    @JsonCreator
    private RegexMatcher(@JsonProperty("pattern") String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public boolean match(String value) {
        return pattern.matcher(value).matches();
    }

    @JsonProperty("pattern")
    private String getPattern() {
        return pattern.pattern();
    }

    @Override
    public String toString() {
        return String.format("RegexMatcher{pattern='%s'}", pattern);
    }

    @Override
    public boolean equals(Object obj) {
        // Custom comparison required: Pattern doesn't implement equals().
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RegexMatcher other = (RegexMatcher) obj;
        return pattern.pattern().equals(other.pattern.pattern());
    }

    @Override
    public int hashCode() {
        // Custom hashcode required: Pattern doesn't implement hashCode(), either.
        return pattern.pattern().hashCode();
    }
}
