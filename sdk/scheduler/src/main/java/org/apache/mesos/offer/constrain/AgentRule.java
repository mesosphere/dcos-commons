package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Ensures that the given Offer is located on the specified agent ID.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class RequireAgentGenerator extends PassthroughGenerator {

        private final String agentId;

        @JsonCreator
        public RequireAgentGenerator(@JsonProperty("agent_id") String agentId) {
            super(new AgentRule(agentId));
            this.agentId = agentId;
        }

        @JsonProperty("agent_id")
        private String getAgentId() {
            return agentId;
        }

        @Override
        public String toString() {
            return String.format("RequireAgentGenerator{agentId=%s}", agentId);
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

    /**
     * Ensures that the given Offer is NOT located on the specified agent ID.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class AvoidAgentGenerator extends PassthroughGenerator {

        private final String agentId;

        @JsonCreator
        public AvoidAgentGenerator(@JsonProperty("agent_id") String agentId) {
            super(new NotRule(new AgentRule(agentId)));
            this.agentId = agentId;
        }

        @JsonProperty("agent_id")
        private String getAgentId() {
            return agentId;
        }

        @Override
        public String toString() {
            return String.format("AvoidAgentGenerator{agentId=%s}", agentId);
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

    /**
     * Ensures that the given Offer is located on one of the specified agent IDs.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class RequireAgentsGenerator extends PassthroughGenerator {

        private final Collection<String> agentIds;

        @JsonCreator
        public RequireAgentsGenerator(@JsonProperty("agent_ids") Collection<String> agentIds) {
            super(new OrRule(toAgentRules(agentIds)));
            this.agentIds = agentIds;
        }

        public RequireAgentsGenerator(String... agentIds) {
            this(Arrays.asList(agentIds));
        }

        @JsonProperty("agent_ids")
        private Collection<String> getAgentIds() {
            return agentIds;
        }

        @Override
        public String toString() {
            return String.format("RequireAgentsGenerator{agentIds=%s}", agentIds);
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

    /**
     * Ensures that the given Offer is NOT located on any of the specified agent IDs.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class AvoidAgentsGenerator extends PassthroughGenerator {

        private final Collection<String> agentIds;

        @JsonCreator
        public AvoidAgentsGenerator(@JsonProperty("agent_ids") Collection<String> agentIds) {
            super(new NotRule(new OrRule(toAgentRules(agentIds))));
            this.agentIds = agentIds;
        }

        public AvoidAgentsGenerator(String... agentIds) {
            this(Arrays.asList(agentIds));
        }

        @JsonProperty("agent_ids")
        private Collection<String> getAgentIds() {
            return agentIds;
        }

        @Override
        public String toString() {
            return String.format("AvoidAgentsGenerator{agentIds=%s}", agentIds);
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
