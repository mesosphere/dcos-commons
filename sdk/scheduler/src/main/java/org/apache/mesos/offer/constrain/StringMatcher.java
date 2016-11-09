package org.apache.mesos.offer.constrain;

import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Implements a check against a provided string, determining whether it matches some criteria.
 * This may be used for checks against e.g. attributes or hostnames.
 *
 * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface StringMatcher {
    public boolean select(String value);

    /**
     * Returns a new {@link StringMatcher} which selects attributes whose string representation
     * exactly matches the provided string.
     *
     * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
     */
    public static StringMatcher createExact(String str) {
        return new ExactMatcher(str);
    }

    /**
     * Returns a new {@link StringMatcher} which selects attributes whose string representation
     * match the provided regular expression.
     *
     * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
     */
    public static StringMatcher createRegex(String pattern) {
        return new RegexMatcher(pattern);
    }

    /**
     * Implements exact string matching support for offer comparisons.
     */
    public static class ExactMatcher implements StringMatcher {

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

    /**
     * Implements fuzzy regular expression support for offer comparisons.
     */
    public static class RegexMatcher implements StringMatcher {

        private final Pattern pattern;

        @JsonCreator
        private RegexMatcher(@JsonProperty("pattern") String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public boolean select(String value) {
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
}
