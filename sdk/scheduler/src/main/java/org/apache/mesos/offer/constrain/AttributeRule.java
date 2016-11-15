package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.AttributeStringUtils;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requires that the Offer contain an attribute whose string representation matches the provided string.
 *
 * @see AttributeStringUtils#toString(Attribute)
 */
public class AttributeRule implements PlacementRule {

    private final StringMatcher matcher;

    @JsonCreator
    public AttributeRule(@JsonProperty("matcher") StringMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        for (Attribute attributeProto : offer.getAttributesList()) {
            String attributeString = AttributeStringUtils.toString(attributeProto);
            if (matcher.matches(attributeString)) {
                // match found. return entire offer as-is
                return offer;
            }
        }
        // match not found: return empty offer
        return offer.toBuilder().clearResources().build();
    }

    @JsonProperty("matcher")
    private StringMatcher getMatcher() {
        return matcher;
    }

    @Override
    public String toString() {
        return String.format("AttributeRule{matcher=%s}", matcher);
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
