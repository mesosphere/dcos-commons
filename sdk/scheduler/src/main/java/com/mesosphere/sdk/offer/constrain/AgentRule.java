package com.mesosphere.sdk.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule enforces that a task be placed on a provided agent ID, or enforces that the task avoid
 * that agent ID, depending on the factory method used.
 */
public class AgentRule implements PlacementRule {

    private final String agentId;

    /**
     * Requires that a task be placed on the provided agent.
     *
     * @param agentId mesos ID of the agent to require
     */
    public static PlacementRule require(String agentId) {
        return new AgentRule(agentId);
    }

    /**
     * Requires that a task be placed on one of the provided agents.
     *
     * @param agentIds mesos ID of the agents to require
     */
    public static PlacementRule require(Collection<String> agentIds) {
        return new OrRule(toAgentRules(agentIds));
    }

    /**
     * Requires that a task be placed on one of the provided agents.
     *
     * @param agentIds mesos ID of the agents to require
     */
    public static PlacementRule require(String... agentIds) {
        return require(Arrays.asList(agentIds));
    }

    /**
     * Requires that a task NOT be placed on the provided agent.
     *
     * @param agentId mesos ID of the agent to avoid
     */
    public static PlacementRule avoid(String agentId) {
        return new NotRule(require(agentId));
    }

    /**
     * Requires that a task NOT be placed on any of the provided agents.
     *
     * @param agentIds mesos ID of the agents to avoid
     */
    public static PlacementRule avoid(Collection<String> agentIds) {
        return new NotRule(require(agentIds));
    }

    /**
     * Requires that a task NOT be placed on any of the provided agents.
     *
     * @param agentIds mesos ID of the agents to avoid
     */
    public static PlacementRule avoid(String... agentIds) {
        return avoid(Arrays.asList(agentIds));
    }

    @JsonCreator
    private AgentRule(@JsonProperty("agent-id") String agentId) {
        this.agentId = agentId;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        if (offer.getSlaveId().getValue().equals(agentId)) {
            return offer;
        } else {
            // agent mismatch: return empty offer
            return offer.toBuilder().clearResources().build();
        }
    }

    @JsonProperty("agent-id")
    private String getAgentId() {
        return agentId;
    }

    @Override
    public String toString() {
        return String.format("AgentRule{agentId=%s}", agentId);
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
