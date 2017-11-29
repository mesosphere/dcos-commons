package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testing.TestPodFactory;
import com.mesosphere.sdk.testutils.TestConstants;
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
        PlacementRule rule = new PassthroughRule();
        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(rule));
        assertFalse(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void simpleRegionRuleSucceeds() {
        PlacementRule rule = new RegionRule(ExactMatcher.create("region"));
        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(rule));
        assertTrue(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void simpleNotRegionRuleFails() {
        PlacementRule rule = new ZoneRule(ExactMatcher.create("zone"));
        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(rule));
        assertFalse(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void orRegionRuleSucceeds() {
        PlacementRule regionRule = new RegionRule(ExactMatcher.create("region"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(orRule));
        assertTrue(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void orNotRegionRuleFails() {
        PlacementRule regionRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(orRule));
        assertFalse(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void nestedOrRegionRuleSucceeds() {
        PlacementRule regionRule = new RegionRule(ExactMatcher.create("region"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PlacementRule attributeRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule andRule = new AndRule(attributeRule, orRule);

        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(andRule));
        assertTrue(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    @Test
    public void nestedOrNotRegionRuleFails() {
        PlacementRule regionRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule zoneRule = new ZoneRule(ExactMatcher.create("zone"));
        PlacementRule orRule = new OrRule(regionRule, zoneRule);

        PlacementRule attributeRule = new AttributeRule(ExactMatcher.create("attribute"));
        PlacementRule andRule = new AndRule(attributeRule, orRule);

        ServiceSpec serviceSpec = getServiceSpec(Arrays.asList(andRule));
        assertFalse(PlacementUtils.placementRulesReferenceRegions(serviceSpec));
    }

    private static ServiceSpec getServiceSpec(Collection<PlacementRule> rules) {
        return DefaultServiceSpec.newBuilder()
                .name(TestConstants.SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("badhost-shouldbeignored:2181")
                .pods(getPods(rules))
                .user(TestConstants.SERVICE_USER)
                .build();
    }

    private static List<PodSpec> getPods(Collection<PlacementRule> rules) {
        List<PodSpec> pods = new ArrayList<>();

        for (PlacementRule rule : rules) {
            PodSpec pod = TestPodFactory.getPodSpec(
                    TestConstants.POD_TYPE,
                    TestConstants.RESOURCE_SET_ID,
                    TestConstants.TASK_NAME,
                    TestConstants.TASK_CMD,
                    TestConstants.SERVICE_USER,
                    1,
                    1.0,
                    256,
                    4096);

            pods.add(
                    DefaultPodSpec.newBuilder(pod)
                            .placementRule(rule)
                            .build());
        }

        return pods;
    }
}
