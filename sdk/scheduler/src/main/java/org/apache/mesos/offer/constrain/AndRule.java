package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

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
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        // Uses Collection.retainAll() to implement a set intersection:
        boolean inited = false;
        Collection<Resource> survivingResources = new ArrayList<>();
        for (PlacementRule rule : rules) {
            if (inited) {
                survivingResources.retainAll(rule.filter(offer, offerRequirement, tasks).getResourcesList());
            } else {
                survivingResources.addAll(rule.filter(offer, offerRequirement, tasks).getResourcesList());
                inited = true;
            }
            if (survivingResources.isEmpty()) {
                // shortcut: all resources are filtered out, stop checking filters
                break;
            }
        }
        return offer.toBuilder().clearResources().addAllResources(survivingResources).build();
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
