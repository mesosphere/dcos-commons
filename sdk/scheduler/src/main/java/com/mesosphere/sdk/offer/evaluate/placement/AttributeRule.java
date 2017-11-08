package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Collection;
import java.util.stream.Collectors;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requires that the Offer contain an attribute whose string representation matches the provided string.
 *
 * @see AttributeStringUtils#toString(Attribute)
 */
public class AttributeRule extends StringMatcherRule {
    private final StringMatcher matcher;

    @JsonCreator
    public AttributeRule(@JsonProperty("matcher") StringMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        if (isAcceptable(matcher, offer, podInstance, tasks)) {
            return EvaluationOutcome.pass(
                    this,
                    "Match found for attribute pattern: '%s'", matcher.toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "None of %d attributes matched pattern: '%s'",
                    offer.getAttributesCount(),
                    matcher.toString())
                    .build();
        }
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

    @Override
    public Collection<String> getKeys(Offer offer) {
        return offer.getAttributesList().stream()
                .map(attribute -> AttributeStringUtils.toString(attribute))
                .collect(Collectors.toList());
    }
}
