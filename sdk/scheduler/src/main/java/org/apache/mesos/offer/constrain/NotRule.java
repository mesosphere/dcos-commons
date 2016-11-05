package org.apache.mesos.offer.constrain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

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
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        Offer filtered = rule.filter(offer, offerRequirement, tasks);
        if (filtered.getResourcesCount() == 0) {
            // shortcut: all resources were filtered out, so return all resources
            return offer;
        } else if (filtered.getResourcesCount() == offer.getResourcesCount()) {
            // other shortcut: no resources were filtered out, so return no resources
            return offer.toBuilder().clearResources().build();
        }
        Set<Resource> resourcesToOmit = new HashSet<>();
        for (Resource resource : filtered.getResourcesList()) {
            resourcesToOmit.add(resource);
        }
        Offer.Builder offerBuilder = offer.toBuilder().clearResources();
        for (Resource resource : offer.getResourcesList()) {
            if (!resourcesToOmit.contains(resource)) {
                offerBuilder.addResources(resource);
            }
        }
        return offerBuilder.build();
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
