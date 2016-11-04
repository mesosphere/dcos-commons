package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
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

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    @Test
    public void testRequireAgent() {
        PlacementRule rule = AgentRule.require(AGENT_1);
        Offer filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());

        rule = AgentRule.require(AGENT_2);
        filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidAgent() {
        PlacementRule rule = AgentRule.avoid(AGENT_1);
        Offer filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());

        rule = AgentRule.avoid(AGENT_2);
        filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testRequireAgents() {
        PlacementRule rule = AgentRule.require(AGENT_1, AGENT_3);
        Offer filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());

        rule = AgentRule.require(AGENT_2, AGENT_3);
        filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidAgents() {
        PlacementRule rule = AgentRule.avoid(AGENT_1, AGENT_3);
        Offer filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());

        rule = AgentRule.avoid(AGENT_2, AGENT_3);
        filtered = rule.filter(offerWithAgent(AGENT_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithAgent(AGENT_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = AgentRule.avoid(AGENT_1, AGENT_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = AgentRule.avoid(AGENT_1);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = AgentRule.require(AGENT_1, AGENT_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = AgentRule.require(AGENT_1);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
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
