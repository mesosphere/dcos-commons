package org.apache.mesos.offer.constrain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.offer.AttributeStringUtils;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requires that the Offer contain an attribute whose string representation matches the provided string.
 *
 * @see AttributeStringUtils#toString(Attribute)
 */
public class AttributeRule implements PlacementRule {

    private final AttributeSelector attributeSelector;

    public AttributeRule(AttributeSelector attributeSelector) {
        this.attributeSelector = attributeSelector;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement) {
        for (Attribute attributeProto : offer.getAttributesList()) {
            String attributeString = AttributeStringUtils.toString(attributeProto);
            if (attributeSelector.select(attributeString)) {
                // match found. return entire offer as-is
                return offer;
            }
        }
        // match not found: return empty offer
        return offer.toBuilder().clearResources().build();
    }

    @Override
    public String toString() {
        return String.format("AttributeRule{selector=%s}", attributeSelector);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * A generator which returns an {@link AttributeRule} for the provided attribute.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class Generator extends PassthroughGenerator {

        private final AttributeSelector attributeSelector;

        @JsonCreator
        public Generator(@JsonProperty("selector") AttributeSelector attributeSelector) {
            super(new AttributeRule(attributeSelector));
            this.attributeSelector = attributeSelector;
        }

        @JsonProperty("selector")
        private AttributeSelector getSelector() {
            return attributeSelector;
        }

        @Override
        public String toString() {
            return String.format("AttributeRuleGenerator{selector=%s}", attributeSelector);
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
}
