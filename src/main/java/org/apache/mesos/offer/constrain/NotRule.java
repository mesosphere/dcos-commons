package org.apache.mesos.offer.constrain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper for another rule which returns the NOT result: Resources which the wrapped rule removed.
 */
public class NotRule implements PlacementRule {

    private final PlacementRule rule;

    public NotRule(PlacementRule rule) {
        this.rule = rule;
    }

    @Override
    public Offer filter(Offer offer) {
        Offer filtered = rule.filter(offer);
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

    /**
     * Wraps the result of the provided {@link PlacementRuleGenerator} in a {@link NotRule}.
     */
    public static class Generator implements PlacementRuleGenerator {

        private final PlacementRuleGenerator generator;

        @JsonCreator
        public Generator(@JsonProperty("generator") PlacementRuleGenerator generator) {
            this.generator = generator;
        }

        @Override
        public PlacementRule generate(Collection<TaskInfo> tasks) {
            return new NotRule(generator.generate(tasks));
        }

        @JsonProperty("generator")
        private PlacementRuleGenerator getGenerator() {
            return generator;
        }

        @Override
        public String toString() {
            return String.format("NotRuleGenerator{generator=%s}", generator);
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
}
