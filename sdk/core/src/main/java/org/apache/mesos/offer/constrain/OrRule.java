package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

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
    public Offer filter(Offer offer) {
        Set<Resource> resourceUnion = new HashSet<>();
        for (PlacementRule rule : rules) {
            Offer filtered = rule.filter(offer);
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

    /**
     * Wraps the result of the provided {@link PlacementRuleGenerator}s in an {@link OrRule}.
     */
    public static class Generator implements PlacementRuleGenerator {

        private final Collection<PlacementRuleGenerator> generators;

        public Generator(Collection<PlacementRuleGenerator> ruleGenerators) {
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
    }
}
