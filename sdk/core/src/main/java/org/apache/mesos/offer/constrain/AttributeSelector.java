package org.apache.mesos.offer.constrain;

import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Implements a check against a provided attribute string, determining whether it matches some
 * criteria. Checks are only performed against the string representations of attributes.
 *
 * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface AttributeSelector {
    public boolean select(String attributeString);

    /**
     * Returns a new {@link AttributeSelector} which selects attributes whose string representation
     * exactly matches the provided string.
     *
     * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
     */
    public static AttributeSelector createStringSelector(String str) {
        return new StringSelector(str);
    }

    /**
     * Returns a new {@link AttributeSelector} which selects attributes whose string representation
     * match the provided regular expression.
     *
     * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
     */
    public static AttributeSelector createRegexSelector(String pattern) {
        return new RegexSelector(pattern);
    }

    /**
     * Implements exact string matching support for offer comparisons.
     */
    public static class StringSelector implements AttributeSelector {

        private final String str;

        @JsonCreator
        private StringSelector(@JsonProperty("string") String str) {
            this.str = str;
        }

        @Override
        public boolean select(String attribute) {
            return str.equals(attribute);
        }

        @JsonProperty("string")
        private String getString() {
            return str;
        }

        @Override
        public String toString() {
            return String.format("StringSelector{str='%s'}", str);
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
    public static class RegexSelector implements AttributeSelector {

        private final Pattern pattern;

        @JsonCreator
        private RegexSelector(@JsonProperty("pattern") String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public boolean select(String attribute) {
            return pattern.matcher(attribute).matches();
        }

        @JsonProperty("pattern")
        private String getPattern() {
            return pattern.pattern();
        }

        @Override
        public String toString() {
            return String.format("RegexSelector{pattern='%s'}", pattern);
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
            RegexSelector other = (RegexSelector) obj;
            return pattern.pattern().equals(other.pattern.pattern());
        }

        @Override
        public int hashCode() {
            // Custom hashcode required: Pattern doesn't implement hashCode(), either.
            return pattern.pattern().hashCode();
        }
    }
}
