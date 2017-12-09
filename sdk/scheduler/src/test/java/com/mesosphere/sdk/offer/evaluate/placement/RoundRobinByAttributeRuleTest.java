package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RoundRobinByAttributeRule}.
 */
public class RoundRobinByAttributeRuleTest extends DefaultCapabilitiesTestSuite {
    private static final StringMatcher MATCHER = RegexMatcher.create("[0-9]");
    private static final PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
            .type("type")
            .count(1)
            .tasks(Arrays.asList(TestPodFactory.getTaskSpec()))
            .build();
    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(podSpec, 0);

    private static final String ATTRIB_NAME = "rack_id";

    private static TaskInfo getTaskInfo(String taskName, String attrVal) {
        return getTaskInfo(taskName, ATTRIB_NAME, attrVal);
    }

    private static TaskInfo getTaskInfo(String taskName, String attrName, String attrVal) {
        TaskInfo.Builder infoBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
                .setName(taskName)
                .setTaskId(CommonIdUtils.toTaskId(taskName));
        infoBuilder.setLabels(new TaskLabelWriter(infoBuilder)
                .setOfferAttributes(offerWithAttribute(attrName, attrVal))
                .toProto());
        return infoBuilder.build();
    }

    private static Offer offerWithAttribute(String value) {
        return offerWithAttribute(ATTRIB_NAME, value);
    }

    private static Offer offerWithAttribute(String name, String value) {
        Protos.Resource resource = ResourceBuilder.fromUnreservedValue(
                "cpus",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
                        .build())
                .build();
        Offer.Builder offerBuilder = OfferTestUtils.getCompleteOffer(resource).toBuilder();
        offerBuilder.addAttributesBuilder()
                .setName(name)
                .setType(Value.Type.TEXT)
                .getTextBuilder().setValue(value);
        return offerBuilder.build();
    }

    private static PodInstance getPodInstance(TaskInfo taskInfo) {
        try {
            TaskLabelReader labels = new TaskLabelReader(taskInfo);
            ResourceSet resourceSet = PodInstanceRequirementTestUtils.getCpuResourceSet(1.0);
            PodSpec podSpec = PodInstanceRequirementTestUtils.getRequirement(
                    resourceSet,
                    labels.getType(),
                    labels.getIndex())
                    .getPodInstance()
                    .getPod();
            return new DefaultPodInstance(podSpec, labels.getIndex());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testRolloutWithAgentCount() throws TaskException, InvalidRequirementException {
        PlacementRule rule = new RoundRobinByAttributeRule(ATTRIB_NAME, Optional.of(3), MATCHER);
        // throw in some preexisting tasks to be ignored by our matcher:
        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(getTaskInfo("ignored1", "value1"));
        tasks.add(getTaskInfo("ignored2", "value2"));
        tasks.add(getTaskInfo("ignored3", "value3"));
        tasks.add(getTaskInfo("ignored4", "value4"));
        tasks.add(getTaskInfo("ignored5", "value5"));
        tasks.add(getTaskInfo("ignored6", "foo", "value6"));
        tasks.add(getTaskInfo("ignored7", "bar", "value7"));
        tasks.add(getTaskInfo("ignored8", "baz", "value8"));
        // 1st task fits on value1:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        TaskInfo taskInfo1 = getTaskInfo("1", "value1");
        PodInstance req1 = getPodInstance(taskInfo1);
        tasks.add(taskInfo1); // value1:1
        // 2nd task doesn't fit on value1 which already has something, but does fit on value2/value3:
        EvaluationOutcome outcome = rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks);
        assertFalse(outcome.toString(), outcome.isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        TaskInfo taskInfo2 = getTaskInfo("2", "value3");
        PodInstance req2 = getPodInstance(taskInfo2);
        tasks.add(taskInfo2); // value1:1, value3:1
        // duplicates of preexisting tasks 1/3 fit on their previous values:
        assertTrue(rule.filter(offerWithAttribute("value1"), req1, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), req2, tasks).isPassing());
        // 3rd task doesnt fit on value1/value3, does fit on value2:
        assertFalse(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("3", "value2")); // value1:1, value2:1, value3:1
        // 4th task fits on any value as the three values now have the same size:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("4", "value2")); // value1:1, value2:2, value3:1
        // 5th task doesn't fit on value2 but does fit on value1/value3:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("5", "value3")); // value1:1, value2:2, value3:2
        // 6th task is launched on erroneous value4 (host4 shouldn't exist: we were told there were only 3 values!)
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("6", "value4")); // value1:1, value2:2, value3:2, value4:1
        // 7th task is launched on value4 as well:
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("7", "value4")); // value1:1, value2:2, value3:2, value4:2
        // 8th task fails to launch on values2-4 as they now all have more occupancy than value1:
        assertFalse(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("8", "value1")); // value1:2, value2:2, value3:2, value4:2
        // now all values1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
    }

    @Test
    public void testRolloutWithoutAgentCount() throws TaskException, InvalidRequirementException {
        PlacementRule rule = new RoundRobinByAttributeRule(ATTRIB_NAME, Optional.empty(), MATCHER);
        // throw in some preexisting tasks to be ignored by our matcher:
        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(getTaskInfo("ignored1", "value1"));
        tasks.add(getTaskInfo("ignored2", "value2"));
        tasks.add(getTaskInfo("ignored3", "value3"));
        tasks.add(getTaskInfo("ignored4", "value4"));
        tasks.add(getTaskInfo("ignored5", "value5"));
        tasks.add(getTaskInfo("ignored6", "foo", "value6"));
        tasks.add(getTaskInfo("ignored7", "bar", "value7"));
        tasks.add(getTaskInfo("ignored8", "baz", "value8"));
        // 1st task fits on value1:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        TaskInfo taskInfo1 = getTaskInfo("1", "value1");
        PodInstance req1 = getPodInstance(taskInfo1);
        tasks.add(taskInfo1); // value1:1
        // 2nd task fits on any of value1/value2/value3, as we don't yet know of other valid values:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        TaskInfo taskInfo2 = getTaskInfo("2", "value3");
        PodInstance req2 = getPodInstance(taskInfo2);
        tasks.add(taskInfo2); // value1:1, value3:1
        // duplicates of preexisting tasks 1/3 fit on their previous values:
        assertTrue(rule.filter(offerWithAttribute("value1"), req1, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), req2, tasks).isPassing());
        // 3rd task fits on any of value1/value2/value3, as all known values have the same amount:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("3", "value2")); // value1:1, value2:1, value3:1
        // 4th task fits on any of value1/value2/value3, as all known values have the same amount:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("4", "value2")); // value1:1, value2:2, value3:1
        // 5th task doesn't fit on value2 but does fit on value1/value3:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("5", "value3")); // value1:1, value2:2, value3:2
        // 6th task is launched on new value4:
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("6", "value4")); // value1:1, value2:2, value3:2, value4:1
        // 7th task is launched on new value4 as well:
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("7", "value4")); // value1:1, value2:2, value3:2, value4:2
        // 8th task fails to launch on values2-4 as they now all have more occupancy than value1:
        assertFalse(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        assertFalse(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        tasks.add(getTaskInfo("8", "value1")); // value1:2, value2:2, value3:2, value4:2
        // now all values1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertTrue(rule.filter(offerWithAttribute("value1"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value2"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value3"), POD_INSTANCE, tasks).isPassing());
        assertTrue(rule.filter(offerWithAttribute("value4"), POD_INSTANCE, tasks).isPassing());
    }
}
