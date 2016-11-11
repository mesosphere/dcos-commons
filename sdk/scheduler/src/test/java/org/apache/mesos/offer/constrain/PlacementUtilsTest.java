package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
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
        Optional<PlacementRule> rule =PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Collections.emptyList());
        assertFalse(rule.isPresent());
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Collections.emptyList());
        assertTrue(rule.get() instanceof NotRule);
        rule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList("colocateme", "colocateme2"));
        assertTrue(rule.get() instanceof OrRule);
        rule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("avoidme", "avoidme2"),
                Arrays.asList("colocateme", "colocateme2"));
        assertTrue(rule.get() instanceof AndRule);
    }

    @Test
    public void testAreEquivalent() throws InvalidRequirementException {
        final TaskInfo origTask = TaskTestUtils.getTaskInfo(Collections.emptyList());
        TaskInfo task = origTask;

        OfferRequirement offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(origTask));
        assertTrue(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));


        task = origTask.toBuilder()
                .setName(TestConstants.TASK_NAME + "b")
                .setTaskId(TaskUtils.toTaskId(TestConstants.TASK_NAME + "b"))
                .build();

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));


        task = origTask.toBuilder().clearLabels().build();

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE + "b", Arrays.asList(origTask));
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));

        offerReq = OfferRequirement.create(TestConstants.TASK_TYPE, Collections.emptyList());
        assertFalse(PlacementUtils.areEquivalent(task, offerReq));
    }
}
