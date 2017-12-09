package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

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
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        if (rules.isEmpty()) {
            return EvaluationOutcome.fail(this, "No rules to AND together is treated as 'always fail'").build();
        }

        int passingCount = 0;
        Collection<EvaluationOutcome> children = new ArrayList<>();
        for (PlacementRule rule : rules) {
            EvaluationOutcome child = rule.filter(offer, podInstance, tasks);
            if (child.isPassing()) {
                passingCount++;
            }
            children.add(child);
        }

        if (passingCount == rules.size()) {
            return EvaluationOutcome.pass(
                    this,
                    "%d of %d rules are passing:", passingCount, rules.size())
                    .addAllChildren(children)
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "%d of %d rules are passing:", passingCount, rules.size())
                    .addAllChildren(children)
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return rules.stream()
                .flatMap(rule -> rule.getPlacementFields().stream())
                .collect(Collectors.toList());
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
