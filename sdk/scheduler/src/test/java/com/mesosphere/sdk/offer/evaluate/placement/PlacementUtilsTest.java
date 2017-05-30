package com.mesosphere.sdk.offer.evaluate.placement;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.*;

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
}
