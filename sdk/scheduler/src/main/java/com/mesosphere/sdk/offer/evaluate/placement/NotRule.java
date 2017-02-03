package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper for another rule which returns the NOT result: Resources which the wrapped rule removed.
 */
public class NotRule implements PlacementRule {

    private final PlacementRule rule;

    @JsonCreator
    public NotRule(@JsonProperty("rule") PlacementRule rule) {
        this.rule = rule;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        EvaluationOutcome child = rule.filter(offer, offerRequirement, tasks);
        return EvaluationOutcome.create(
                !child.isPassing(), this, null, Arrays.asList(child), "Returning opposite of child rule:");
    }

    @JsonProperty("rule")
    private PlacementRule getRule() {
        return rule;
    }

    @Override
    public String toString() {
        return String.format("NotRule{rule=%s}", rule);
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
