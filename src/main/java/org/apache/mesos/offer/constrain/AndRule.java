package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

/**
 * Wrapper for one or more another rules which returns the AND/intersection of those rules.
 */
public class AndRule implements PlacementRule {

    private final Collection<PlacementRule> rules;

    public AndRule(Collection<PlacementRule> rules) {
        this.rules = rules;
    }

    public AndRule(PlacementRule... rules) {
        this(Arrays.asList(rules));
    }

    @Override
    public Offer filter(Offer offer) {
        // Counts of how often each Resource passed each filter:
        Map<Resource, Integer> resourceCounts = new HashMap<>();
        for (PlacementRule rule : rules) {
            Offer filtered = rule.filter(offer);
            for (Resource resource : filtered.getResourcesList()) {
                Integer val = resourceCounts.get(resource);
                if (val == null) {
                    val = 0;
                }
                val++;
                resourceCounts.put(resource, val);
            }
        }
        if (resourceCounts.size() == 0) {
            // shortcut: all resources were filtered out, so return no resources
            return offer.toBuilder().clearResources().build();
        }
        // preserve original ordering (and any original duplicates): test the original list in order
        Offer.Builder offerBuilder = offer.toBuilder().clearResources();
        for (Resource resource : offer.getResourcesList()) {
            Integer val = resourceCounts.get(resource);
            if (val != null && val == rules.size()) {
                offerBuilder.addResources(resource);
            }
        }
        return offerBuilder.build();
    }

    @Override
    public String toString() {
        return String.format("AndRule{rules=%s}", rules);
    }

    /**
     * Wraps the result of the provided {@link PlacementRuleGenerator}s in an {@link AndRule}.
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
            return new AndRule(rules);
        }
    }
}
