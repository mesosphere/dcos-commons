package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
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
 * Wrapper for one or more another rules which returns the AND/intersection of those rules.
 */
public class AndRule implements PlacementRule {

    private final Collection<PlacementRule> rules;

    @JsonCreator
    public AndRule(@JsonProperty("rules") Collection<PlacementRule> rules) {
        this.rules = rules;
    }

    public AndRule(PlacementRule... rules) {
        this(Arrays.asList(rules));
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        if (rules.isEmpty()) {
            return EvaluationOutcome.fail(this, "No rules to AND together is treated as 'always fail'");
        }
        int passingCount = 0;
        Collection<EvaluationOutcome> children = new ArrayList<>();
        for (PlacementRule rule : rules) {
            EvaluationOutcome child = rule.filter(offer, offerRequirement, tasks);
            if (child.isPassing()) {
                passingCount++;
            }
            children.add(child);
        }
        return EvaluationOutcome.create(
                passingCount == rules.size(),
                this,
                null,
                children,
                "%d of %d rules are passing:", passingCount, rules.size());
    }

    @JsonProperty("rules")
    private Collection<PlacementRule> getRules() {
        return rules;
    }

    @Override
    public String toString() {
        return String.format("AndRule{rules=%s}", rules);
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
