package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.TaskInfo;
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

    @Test
    public void testAreEquivalent() throws InvalidRequirementException, TaskException {
        final TaskInfo origTask = TaskTestUtils.getTaskInfo(Collections.emptyList());
        TaskInfo task = origTask;

        OfferRequirement offerReq = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(origTask),
                Arrays.asList(origTask));
        assertTrue(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", 0, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));


        task = origTask.toBuilder()
                .setName(TestConstants.TASK_NAME + "b")
                .setTaskId(CommonTaskUtils.toTaskId(TestConstants.TASK_NAME + "b"))
                .build();

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", 0, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));


        task = origTask.toBuilder().clearLabels().build();

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", 0, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));
    }
}
