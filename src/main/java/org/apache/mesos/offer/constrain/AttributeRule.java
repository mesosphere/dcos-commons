package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.offer.AttributeStringUtils;

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
    public Offer filter(Offer offer) {
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

    /**
     * A generator which returns an {@link AttributeRule} for the provided attribute.
     */
    public static class Generator extends PassthroughGenerator {
        public Generator(AttributeSelector attributeSelector) {
            super(new AttributeRule(attributeSelector));
        }
    }
}
