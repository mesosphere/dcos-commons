package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link PlacementUtils}.
 */
public class PlacementUtilsTest {
    @Test
    public void testGetAgentPlacementRule() {
        Optional<PlacementRule> rule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Collections.emptyList());
        assertFalse(rule.isPresent());
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Collections.emptyList());
        assertEquals("NotRule{rule=OrRule{rules=[AgentRule{agentId=avoidme}, AgentRule{agentId=avoidme2}]}}", rule.get().toString());
        rule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList("colocateme", "colocateme2"));
        assertEquals("OrRule{rules=[AgentRule{agentId=colocateme}, AgentRule{agentId=colocateme2}]}", rule.get().toString());
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Arrays.asList("colocateme", "colocateme2"));
        assertEquals("AndRule{rules=[NotRule{rule=OrRule{rules=[AgentRule{agentId=avoidme}, AgentRule{agentId=avoidme2}]}}, " +
                "OrRule{rules=[AgentRule{agentId=colocateme}, AgentRule{agentId=colocateme2}]}]}", rule.get().toString());
    }

    @Test
    public void emptyRuleNotRegion() {
        PodSpec podSpec = getPodSpec();
        assertFalse(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void passThroughRuleNotRegion() {
        PlacementRule rule = new PassthroughRule();
        PodSpec podSpec = getPodSpec(rule);
        assertFalse(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void simpleRegionRuleSucceeds() {
        PlacementRule rule = new RegionRule(ExactMatcher.create("region"));
        PodSpec podSpec = getPodSpec(rule);
        assertTrue(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void simpleNotRegionRuleFails() {
        PlacementRule rule = new ZoneRule(ExactMatcher.create("zone"));
        PodSpec podSpec = getPodSpec(rule);
        assertFalse(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void orRegionRuleSucceeds() {
        PlacementRule regionRule = new RegionRule(ExactMatcher.create("region"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PodSpec podSpec = getPodSpec(orRule);
        assertTrue(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void orNotRegionRuleFails() {
        PlacementRule regionRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PodSpec podSpec = getPodSpec(orRule);
        assertFalse(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void nestedOrRegionRuleSucceeds() {
        PlacementRule regionRule = new RegionRule(ExactMatcher.create("region"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PlacementRule attributeRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule andRule = new AndRule(attributeRule, orRule);

        PodSpec podSpec = getPodSpec(andRule);
        assertTrue(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    @Test
    public void nestedOrNotRegionRuleFails() {
        PlacementRule regionRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PlacementRule attributeRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule andRule = new AndRule(attributeRule, orRule);

        PodSpec podSpec = getPodSpec(andRule);
        assertFalse(PlacementUtils.placementRuleReferencesRegion(podSpec));
    }

    private static PodSpec getPodSpec() {
        return TestPodFactory.getPodSpec(
                TestConstants.POD_TYPE,
                TestConstants.RESOURCE_SET_ID,
                TestConstants.TASK_NAME,
                TestConstants.TASK_CMD,
                TestConstants.SERVICE_USER,
                1,
                1.0,
                256,
                4096);
    }

    private static PodSpec getPodSpec(PlacementRule rule) {
        return DefaultPodSpec.newBuilder(getPodSpec())
                .placementRule(rule)
                .build();
    }
}
