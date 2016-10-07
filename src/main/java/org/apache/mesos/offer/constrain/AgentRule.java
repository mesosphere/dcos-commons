package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.mesos.Protos.Offer;

/**
 * This rule enforces that a task be placed on a provided agent ID, or enforces that the task avoid
 * that agent ID.
 */
public class AgentRule implements PlacementRule {

    private final String agentId;

    public AgentRule(String agentId) {
        this.agentId = agentId;
    }

    @Override
    public Offer filter(Offer offer) {
        if (offer.getSlaveId().getValue().equals(agentId)) {
            return offer;
        } else {
            // agent mismatch: return empty offer
            return offer.toBuilder().clearResources().build();
        }
    }

    @Override
    public String toString() {
        return String.format("AgentRule{agentId=%s}", agentId);
    }

    /**
     * Ensures that the given Offer is colocated with the specified agent ID.
     */
    public static class ColocateAgentGenerator extends PassthroughGenerator {

        public ColocateAgentGenerator(String agentId) {
            super(new AgentRule(agentId));
        }
    }

    /**
     * Ensures that the given Offer is NOT colocated with the specified agent ID.
     */
    public static class AvoidAgentGenerator extends PassthroughGenerator {

        public AvoidAgentGenerator(String agentId) {
            super(new NotRule(new AgentRule(agentId)));
        }
    }

    /**
     * Ensures that the given Offer is colocated with one of the specified agent IDs.
     */
    public static class ColocateAgentsGenerator extends PassthroughGenerator {

        public ColocateAgentsGenerator(Collection<String> agentIds) {
            super(new OrRule(toAgentRules(agentIds)));
        }

        public ColocateAgentsGenerator(String... agentIds) {
            this(Arrays.asList(agentIds));
        }
    }

    /**
     * Ensures that the given Offer is NOT colocated with any of the specified agent IDs.
     */
    public static class AvoidAgentsGenerator extends PassthroughGenerator {

        public AvoidAgentsGenerator(Collection<String> agentIds) {
            super(new NotRule(new OrRule(toAgentRules(agentIds))));
        }

        public AvoidAgentsGenerator(String... agentIds) {
            this(Arrays.asList(agentIds));
        }
    }

    /**
     * Converts the provided agent ids into {@link AgentRule}s.
     */
    private static Collection<PlacementRule> toAgentRules(Collection<String> agentIds) {
        List<PlacementRule> rules = new ArrayList<>();
        for (String agentId : agentIds) {
            rules.add(new AgentRule(agentId));
        }
        return rules;
    }
}
