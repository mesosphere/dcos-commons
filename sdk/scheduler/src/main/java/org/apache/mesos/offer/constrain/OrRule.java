package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
 * Wrapper for one or more another rules which returns the OR/union of those rules.
 */
public class OrRule implements PlacementRule {

    private final Collection<PlacementRule> rules;

    public OrRule(Collection<PlacementRule> rules) {
        this.rules = rules;
    }

    public OrRule(PlacementRule... rules) {
        this(Arrays.asList(rules));
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement) {
        Set<Resource> resourceUnion = new HashSet<>();
        for (PlacementRule rule : rules) {
            Offer filtered = rule.filter(offer, offerRequirement);
            for (Resource resource : filtered.getResourcesList()) {
                resourceUnion.add(resource);
            }
            if (resourceUnion.size() == offer.getResourcesCount()) {
                // shortcut: all resources passing one or more filters, so return all resources
                return offer;
            }
        }
        if (resourceUnion.size() == 0) {
            // shortcut: all resources were filtered out, so return no resources
            return offer.toBuilder().clearResources().build();
        }
        // preserve original ordering (and any original duplicates): test the original list in order
        Offer.Builder offerBuilder = offer.toBuilder().clearResources();
        for (Resource resource : offer.getResourcesList()) {
            if (resourceUnion.contains(resource)) {
                offerBuilder.addResources(resource);
            }
        }
        return offerBuilder.build();
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

    /**
     * Wraps the result of the provided {@link PlacementRuleGenerator}s in an {@link OrRule}.
     */
    public static class Generator implements PlacementRuleGenerator {

        private final Collection<PlacementRuleGenerator> generators;

        @JsonCreator
        public Generator(@JsonProperty("generators") Collection<PlacementRuleGenerator> ruleGenerators) {
            this.generators = ruleGenerators;
        }

        public Generator(PlacementRuleGenerator... ruleGenerators) {
            this(Arrays.asList(ruleGenerators));
        }

        @Override
        public PlacementRule generate(Collection<TaskInfo> tasks) {
            List<PlacementRule> rules = new ArrayList<>();
            for (PlacementRuleGenerator ruleGenerator : generators) {
                rules.add(ruleGenerator.generate(tasks));
            }
            return new OrRule(rules);
        }

        @JsonProperty("generators")
        private Collection<PlacementRuleGenerator> getGenerators() {
            return generators;
        }

        @Override
        public String toString() {
            return String.format("OrRuleGenerator{generators=%s}", generators);
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
