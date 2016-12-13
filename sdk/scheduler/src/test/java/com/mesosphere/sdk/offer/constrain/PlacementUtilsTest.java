package com.mesosphere.sdk.offer.constrain;

import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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
        assertEquals("NotRule{rule=OrRule{rules=[" +
                "AgentRule{agentId=avoidme}, " +
                "AgentRule{agentId=avoidme2}" +
                "]}}", rule.get().toString());
        rule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList("colocateme", "colocateme2"));
        assertEquals("OrRule{rules=[" +
                "AgentRule{agentId=colocateme}, " +
                "AgentRule{agentId=colocateme2}" +
                "]}", rule.get().toString());
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Arrays.asList("colocateme", "colocateme2"));
        assertEquals("AndRule{rules=[" +
                "NotRule{rule=OrRule{rules=[" +
                "AgentRule{agentId=avoidme}, " +
                "AgentRule{agentId=avoidme2}" +
                "]}}, " +
                "OrRule{rules=[" +
                "AgentRule{agentId=colocateme}, " +
                "AgentRule{agentId=colocateme2}" +
                "]}" +
                "]}", rule.get().toString());
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme"),
                Arrays.asList("colocateme"));
        assertEquals("AndRule{rules=[" +
                "NotRule{rule=AgentRule{agentId=avoidme}}, " +
                "AgentRule{agentId=colocateme}" +
                "]}", rule.get().toString());
    }

    @Test
    public void testGetTaskTypePlacementRule() {
        // all empty/missing:
        Optional<PlacementRule> rule = PlacementUtils.getTaskTypePlacementRule(
                Collections.emptyList(),
                Collections.emptyList(),
                Optional.empty());
        assertFalse(rule.isPresent());

        // agents provided, no custom:
        rule = PlacementUtils.getTaskTypePlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Collections.emptyList(),
                Optional.empty());
        assertEquals("AndRule{rules=[" +
                "TaskTypeRule{type=avoidme, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "TaskTypeRule{type=avoidme2, converter=TaskTypeLabelConverter{}, behavior=AVOID}]}", rule.get().toString());
        rule = PlacementUtils.getTaskTypePlacementRule(
                Collections.emptyList(),
                Arrays.asList("colocateme", "colocateme2"),
                Optional.empty());
        assertEquals("OrRule{rules=[" +
                "TaskTypeRule{type=colocateme, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "TaskTypeRule{type=colocateme2, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}]}", rule.get().toString());
        rule = PlacementUtils.getTaskTypePlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Arrays.asList("colocateme", "colocateme2"),
                Optional.empty());
        assertEquals("AndRule{rules=[" +
                "AndRule{rules=[" +
                "TaskTypeRule{type=avoidme, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "TaskTypeRule{type=avoidme2, converter=TaskTypeLabelConverter{}, behavior=AVOID}]}, " +
                "OrRule{rules=[" +
                "TaskTypeRule{type=colocateme, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "TaskTypeRule{type=colocateme2, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}]}]}", rule.get().toString());

        // custom provided, no agents:
        rule = PlacementUtils.getTaskTypePlacementRule(
                Collections.emptyList(),
                Collections.emptyList(),
                Optional.of(new PassthroughRule()));
        assertEquals("PassthroughRule{}", rule.get().toString());

        // agents provided with custom:
        rule = PlacementUtils.getTaskTypePlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Collections.emptyList(),
                Optional.of(new PassthroughRule()));
        assertEquals("AndRule{rules=[" +
                "AndRule{rules=[" +
                "TaskTypeRule{type=avoidme, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "TaskTypeRule{type=avoidme2, converter=TaskTypeLabelConverter{}, behavior=AVOID}" +
                "]}, " +
                "PassthroughRule{}" +
                "]}", rule.get().toString());
        rule = PlacementUtils.getTaskTypePlacementRule(
                Collections.emptyList(),
                Arrays.asList("colocateme", "colocateme2"),
                Optional.of(new PassthroughRule()));
        assertEquals("AndRule{rules=[" +
                "OrRule{rules=[" +
                "TaskTypeRule{type=colocateme, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "TaskTypeRule{type=colocateme2, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}" +
                "]}, " +
                "PassthroughRule{}" +
                "]}", rule.get().toString());
        rule = PlacementUtils.getTaskTypePlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Arrays.asList("colocateme", "colocateme2"),
                Optional.of(new PassthroughRule()));
        assertEquals("AndRule{rules=[" +
                "AndRule{rules=[" +
                "TaskTypeRule{type=avoidme, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "TaskTypeRule{type=avoidme2, converter=TaskTypeLabelConverter{}, behavior=AVOID}" +
                "]}, " +
                "OrRule{rules=[" +
                "TaskTypeRule{type=colocateme, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "TaskTypeRule{type=colocateme2, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}" +
                "]}, " +
                "PassthroughRule{}" +
                "]}", rule.get().toString());
    }

    @Test
    public void testAreEquivalent() throws InvalidRequirementException {
        final TaskInfo origTask = TaskTestUtils.getTaskInfo(Collections.emptyList());
        TaskInfo task = origTask;

        OfferRequirement offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(origTask));
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
