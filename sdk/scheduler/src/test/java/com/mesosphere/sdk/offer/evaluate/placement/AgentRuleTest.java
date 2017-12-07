package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos.Offer;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link AgentRule}.
 */
public class AgentRuleTest {
    private static final String AGENT_1 = "agent-1-uuid";
    private static final String AGENT_2 = "agent-2-uuid";
    private static final String AGENT_3 = "agent-3-uuid";

    private static final PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
            .type("type")
            .count(1)
            .tasks(Arrays.asList(TestPodFactory.getTaskSpec()))
            .build();
    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(podSpec, 0);

    @Test
    public void testRequireAgent() {
        PlacementRule rule = AgentRule.require(AGENT_1);
        assertTrue(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = AgentRule.require(AGENT_2);
        assertFalse(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAvoidAgent() {
        PlacementRule rule = AgentRule.avoid(AGENT_1);
        assertFalse(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = AgentRule.avoid(AGENT_2);
        assertTrue(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testRequireAgents() {
        PlacementRule rule = AgentRule.require(AGENT_1, AGENT_3);
        assertTrue(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = AgentRule.require(AGENT_2, AGENT_3);
        assertFalse(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAvoidAgents() {
        PlacementRule rule = AgentRule.avoid(AGENT_1, AGENT_3);
        assertFalse(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = AgentRule.avoid(AGENT_2, AGENT_3);
        assertTrue(rule.filter(offerWithAgent(AGENT_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithAgent(AGENT_3), POD_INSTANCE, Collections.emptyList()).isPassing());
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
