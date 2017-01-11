package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link RoundRobinByAttributeRule}.
 */
public class RoundRobinByAttributeRuleTest {

    private static final StringMatcher MATCHER = RegexMatcher.create("[0-9]");
    private static OfferRequirement REQ;

    @BeforeClass
    public static void beforeAll() throws InvalidRequirementException {
        REQ = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Collections.emptyList());
    }

    private static final String ATTRIB_NAME = "rack_id";

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
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo1 = getTaskInfo("1", "value1");
        OfferRequirement req1 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo1),
                Arrays.asList(taskInfo1));
        tasks.add(taskInfo1); // value1:1
        // 2nd task doesn't fit on value1 which already has something, but does fit on value2/value3:
        assertEquals(0, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo2 = getTaskInfo("2", "value3");
        OfferRequirement req2 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo2),
                Arrays.asList(taskInfo2));
        tasks.add(taskInfo2); // value1:1, value3:1
        // duplicates of preexisting tasks 1/3 fit on their previous values:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), req1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), req2, tasks).getResourcesCount());
        // 3rd task doesnt fit on value1/value3, does fit on value2:
        assertEquals(0, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("3", "value2")); // value1:1, value2:1, value3:1
        // 4th task fits on any value as the three values now have the same size:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("4", "value2")); // value1:1, value2:2, value3:1
        // 5th task doesn't fit on value2 but does fit on value1/value3:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("5", "value3")); // value1:1, value2:2, value3:2
        // 6th task is launched on erroneous value4 (host4 shouldn't exist: we were told there were only 3 values!)
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("6", "value4")); // value1:1, value2:2, value3:2, value4:1
        // 7th task is launched on value4 as well:
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("7", "value4")); // value1:1, value2:2, value3:2, value4:2
        // 8th task fails to launch on values2-4 as they now all have more occupancy than value1:
        assertEquals(0, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("8", "value1")); // value1:2, value2:2, value3:2, value4:2
        // now all values1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
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
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo1 = getTaskInfo("1", "value1");
        OfferRequirement req1 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo1),
                Arrays.asList(taskInfo1));
        tasks.add(taskInfo1); // value1:1
        // 2nd task fits on any of value1/value2/value3, as we don't yet know of other valid values:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo2 = getTaskInfo("2", "value3");
        OfferRequirement req2 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo2),
                Arrays.asList(taskInfo2));
        tasks.add(taskInfo2); // value1:1, value3:1
        // duplicates of preexisting tasks 1/3 fit on their previous values:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), req1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), req2, tasks).getResourcesCount());
        // 3rd task fits on any of value1/value2/value3, as all known values have the same amount:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("3", "value2")); // value1:1, value2:1, value3:1
        // 4th task fits on any of value1/value2/value3, as all known values have the same amount:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("4", "value2")); // value1:1, value2:2, value3:1
        // 5th task doesn't fit on value2 but does fit on value1/value3:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("5", "value3")); // value1:1, value2:2, value3:2
        // 6th task is launched on new value4:
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("6", "value4")); // value1:1, value2:2, value3:2, value4:1
        // 7th task is launched on new value4 as well:
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("7", "value4")); // value1:1, value2:2, value3:2, value4:2
        // 8th task fails to launch on values2-4 as they now all have more occupancy than value1:
        assertEquals(0, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("8", "value1")); // value1:2, value2:2, value3:2, value4:2
        // now all values1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertEquals(1, rule.filter(offerWithAttribute("value1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value3"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithAttribute("value4"), REQ, tasks).getResourcesCount());
    }

    private static TaskInfo getTaskInfo(String taskName, String attrVal) {
        return getTaskInfo(taskName, ATTRIB_NAME, attrVal);
    }

    private static TaskInfo getTaskInfo(String taskName, String attrName, String attrVal) {
        TaskInfo.Builder infoBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
                .setName(taskName)
                .setTaskId(CommonTaskUtils.toTaskId(taskName));
        return CommonTaskUtils.setOfferAttributes(infoBuilder, offerWithAttribute(attrName, attrVal)).build();
    }

    private static Offer offerWithAttribute(String value) {
        return offerWithAttribute(ATTRIB_NAME, value);
    }

    private static Offer offerWithAttribute(String name, String value) {
        Offer.Builder offerBuilder = OfferTestUtils.getOffer(ResourceTestUtils.getDesiredCpu(1.0)).toBuilder();
        offerBuilder.addAttributesBuilder()
                .setName(name)
                .setType(Value.Type.TEXT)
                .getTextBuilder().setValue(value);
        return offerBuilder.build();
    }
}
