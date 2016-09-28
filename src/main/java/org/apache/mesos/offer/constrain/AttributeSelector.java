package org.apache.mesos.offer.constrain;

import java.util.regex.Pattern;

/**
 * Implements a check against a provided attribute string, determining whether it matches some
 * criteria. Checks are only performed against the string representations of attributes.
 *
 * @see org.apache.mesos.offer.AttributeStringUtils#toString(org.apache.mesos.Protos.Attribute)
 */
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
     * Implements exact string matching support for agent attribute comparisons.
     */
    public static class StringSelector implements AttributeSelector {

        private final String str;

        private StringSelector(String str) {
            this.str = str;
        }

        @Override
        public boolean select(String attribute) {
            return str.equals(attribute);
        }

        @Override
        public String toString() {
            return String.format("StringSelector{str=%s}", str);
        }
    }

    /**
     * Implements regex support for agent attribute comparisons.
     */
    public static class RegexSelector implements AttributeSelector {

        private final Pattern pattern;

        private RegexSelector(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public boolean select(String attribute) {
            return pattern.matcher(attribute).matches();
        }

        @Override
        public String toString() {
            return String.format("RegexSelector{pattern=%s}", pattern);
        }
    }
}
