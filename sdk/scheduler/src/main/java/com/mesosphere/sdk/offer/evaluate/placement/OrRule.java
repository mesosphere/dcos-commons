package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper for one or more another rules which returns the OR/union of those rules.
 */
public class OrRule implements PlacementRule {

    private final Collection<PlacementRule> rules;

    @JsonCreator
    public OrRule(@JsonProperty("rules") Collection<PlacementRule> rules) {
        this.rules = rules;
    }

    public OrRule(PlacementRule... rules) {
        this(Arrays.asList(rules));
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        int passingCount = 0;
        Collection<EvaluationOutcome> children = new ArrayList<>();
        for (PlacementRule rule : rules) {
            EvaluationOutcome child = rule.filter(offer, podInstance, tasks);
            if (child.isPassing()) {
                passingCount++;
            }
            children.add(child);
        }
        return EvaluationOutcome.create(
                passingCount != 0,
                this,
                null,
                Collections.emptyList(),
                children,
                "%d of %d rules are passing:", passingCount, rules.size());
    }

    @JsonProperty("rules")
    private Collection<PlacementRule> getRules() {
        return rules;
    }

    @Override
    public String toString() {
        return String.format("OrRule{rules=%s}", rules);
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
