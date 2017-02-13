package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.AttributeStringUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requires that the Offer contain an attribute whose string representation matches the provided string.
 *
 * @see AttributeStringUtils#toString(Attribute)
 */
public class AttributeRule implements PlacementRule {

    /**
     * Requires that a task be placed on the provided attribute matcher.
     *
     * @param matcher matcher for attribute to require
     */
    public static PlacementRule require(StringMatcher matcher) {
        return new AttributeRule(matcher);
    }

    /**
     * Requires that a task be placed on one of the provided attribute matchers.
     *
     * @param matchers matchers for attributes to require
     */
    public static PlacementRule require(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return require(matchers.iterator().next());
        }
        List<PlacementRule> rules = new ArrayList<>();
        for (StringMatcher matcher : matchers) {
            rules.add(require(matcher));
        }
        return new OrRule(rules);
    }

    /**
     * Requires that a task be placed on one of the provided attribute matchers.
     *
     * @param matchers matchers for attributes to require
     */
    public static PlacementRule require(StringMatcher... matchers) {
        return require(Arrays.asList(matchers));
    }

    /**
     * Requires that a task NOT be placed on the provided attribute matcher.
     *
     * @param matcher matcher for attribute to avoid
     */
    public static PlacementRule avoid(StringMatcher matcher) {
        return new NotRule(require(matcher));
    }

    /**
     * Requires that a task NOT be placed on any of the provided attribute matchers.
     *
     * @param matchers matchers for attributes to avoid
     */
    public static PlacementRule avoid(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return avoid(matchers.iterator().next());
        }
        return new NotRule(require(matchers));
    }

    /**
     * Requires that a task NOT be placed on any of the provided attribute matchers.
     *
     * @param matchers matchers for attributes to avoid
     */
    public static PlacementRule avoid(StringMatcher... matchers) {
        return avoid(Arrays.asList(matchers));
    }

    private final StringMatcher matcher;

    @JsonCreator
    private AttributeRule(@JsonProperty("matcher") StringMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        for (Attribute attributeProto : offer.getAttributesList()) {
            String attributeString = AttributeStringUtils.toString(attributeProto);
            if (matcher.matches(attributeString)) {
                return EvaluationOutcome.pass(
                        this, "Match found for attribute pattern: '%s'", matcher.toString());
            }
        }
        return EvaluationOutcome.fail(this, "None of %d attributes matched pattern: '%s'",
                offer.getAttributesCount(), matcher.toString());
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
