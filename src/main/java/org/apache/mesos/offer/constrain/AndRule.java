package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
        // Uses Collection.retainAll() to implement a set intersection:
        boolean inited = false;
        Collection<Resource> survivingResources = new ArrayList<>();
        for (PlacementRule rule : rules) {
            if (inited) {
                survivingResources.retainAll(rule.filter(offer).getResourcesList());
            } else {
                survivingResources.addAll(rule.filter(offer).getResourcesList());
                inited = true;
            }
            if (survivingResources.isEmpty()) {
                // shortcut: all resources are filtered out, stop checking filters
                break;
            }
        }
        return offer.toBuilder().clearResources().addAllResources(survivingResources).build();
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
