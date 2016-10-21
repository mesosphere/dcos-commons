package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;

/**
 * Tests for {@link AgentRule}.
 */
public class AgentRuleTest {
    private static final String AGENT_1 = "agent-1-uuid";
    private static final String AGENT_2 = "agent-2-uuid";
    private static final String AGENT_3 = "agent-3-uuid";

    @Test
    public void testRequireAgent() {
        PlacementRule rule = new AgentRule.RequireAgentGenerator(AGENT_1).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(0, filtered.getResourcesCount());

        rule = new AgentRule.RequireAgentGenerator(AGENT_2).generate(Collections.emptyList());
        filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidAgent() {
        PlacementRule rule = new AgentRule.AvoidAgentGenerator(AGENT_1).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(3, filtered.getResourcesCount());

        rule = new AgentRule.AvoidAgentGenerator(AGENT_2).generate(Collections.emptyList());
        filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testRequireAgents() {
        PlacementRule rule = new AgentRule.RequireAgentsGenerator(AGENT_1, AGENT_3).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(3, filtered.getResourcesCount());

        rule = new AgentRule.RequireAgentsGenerator(AGENT_2, AGENT_3).generate(Collections.emptyList());
        filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidAgents() {
        PlacementRule rule = new AgentRule.AvoidAgentsGenerator(AGENT_1, AGENT_3).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(0, filtered.getResourcesCount());

        rule = new AgentRule.AvoidAgentsGenerator(AGENT_2, AGENT_3).generate(Collections.emptyList());
        filtered = rule.filter(offerWithAgent(AGENT_1));
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2));
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3));
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = new AgentRule.AvoidAgentsGenerator(AGENT_1, AGENT_3);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new AgentRule.AvoidAgentGenerator(AGENT_1);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new AgentRule.RequireAgentsGenerator(AGENT_1, AGENT_3);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new AgentRule.RequireAgentGenerator(AGENT_1);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
    }

    private static Offer offerWithAgent(String agentId) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        o.getSlaveIdBuilder().setValue(agentId);
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        OfferTestUtils.addResource(o, "c");
        return o.build();
    }
}
