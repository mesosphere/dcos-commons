package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link RoundRobinByHostnameRule}.
 */
public class RoundRobinByHostnameRuleTest {

    private static final StringMatcher MATCHER = RegexMatcher.create("[0-9]");
    private static OfferRequirement REQ;

    @BeforeClass
    public static void beforeAll() throws InvalidRequirementException {
        REQ = OfferRequirement.create(TestConstants.TASK_TYPE, 0, Collections.emptyList());
    }

    @Test
    public void testRolloutWithAgentCount() throws TaskException, InvalidRequirementException {
        PlacementRule rule = new RoundRobinByHostnameRule(Optional.of(3), MATCHER);
        // throw in some preexisting tasks to be ignored by our matcher:
        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(getTaskInfo("ignored1", "host1"));
        tasks.add(getTaskInfo("ignored2", "host2"));
        tasks.add(getTaskInfo("ignored3", "host3"));
        tasks.add(getTaskInfo("ignored4", "host1"));
        tasks.add(getTaskInfo("ignored5", "host2"));
        // 1st task fits on host1:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo1 = getTaskInfo("1", "host1");
        OfferRequirement req1 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo1),
                Arrays.asList(taskInfo1));
        tasks.add(taskInfo1); // host1:1
        // 2nd task doesn't fit on host1 which already has something, but does fit on host2/host3:
        assertEquals(0, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo2 = getTaskInfo("2", "host3");
        OfferRequirement req2 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo2),
                Arrays.asList(taskInfo2));
        tasks.add(taskInfo2); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), req1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), req2, tasks).getResourcesCount());
        // 3rd task doesnt fit on host1/host3, does fit on host2:
        assertEquals(0, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("3", "host2")); // host1:1, host2:1, host3:1
        // 4th task fits on any host as the three hosts now have the same size:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("4", "host2")); // host1:1, host2:2, host3:1
        // 5th task doesn't fit on host2 but does fit on host1/host3:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("5", "host3")); // host1:1, host2:2, host3:2
        // 6th task is launched on erroneous host4 (host4 shouldn't exist: we were told there were only 3 hosts!)
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("6", "host4")); // host1:1, host2:2, host3:2, host4:1
        // 7th task is launched on host4 as well:
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("7", "host4")); // host1:1, host2:2, host3:2, host4:2
        // 8th task fails to launch on hosts2-4 as they now all have more occupancy than host1:
        assertEquals(0, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("8", "host1")); // host1:2, host2:2, host3:2, host4:2
        // now all hosts1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
    }

    @Test
    public void testRolloutWithoutAgentCount() throws TaskException, InvalidRequirementException {
        PlacementRule rule = new RoundRobinByHostnameRule(Optional.empty(), MATCHER);
        // throw in some preexisting tasks to be ignored by our matcher:
        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(getTaskInfo("ignored1", "host1"));
        tasks.add(getTaskInfo("ignored2", "host2"));
        tasks.add(getTaskInfo("ignored3", "host3"));
        tasks.add(getTaskInfo("ignored4", "host1"));
        tasks.add(getTaskInfo("ignored5", "host2"));
        // 1st task fits on host1:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo1 = getTaskInfo("1", "host1");
        OfferRequirement req1 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo1),
                Arrays.asList(taskInfo1));
        tasks.add(taskInfo1); // host1:1
        // 2nd task fits on any of host1/host2/host3, as we don't yet know of other valid hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        TaskInfo taskInfo2 = getTaskInfo("2", "host3");
        OfferRequirement req2 = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                CommonTaskUtils.getIndex(taskInfo2),
                Arrays.asList(taskInfo2));
        tasks.add(taskInfo2); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), req1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), req2, tasks).getResourcesCount());
        // 3rd task fits on any of host1/host2/host3, as all known hosts have the same amount:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("3", "host2")); // host1:1, host2:1, host3:1
        // 4th task fits on any of host1/host2/host3, as all known hosts have the same amount:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("4", "host2")); // host1:1, host2:2, host3:1
        // 5th task doesn't fit on host2 but does fit on host1/host3:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("5", "host3")); // host1:1, host2:2, host3:2
        // 6th task is launched on new host4:
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("6", "host4")); // host1:1, host2:2, host3:2, host4:1
        // 7th task is launched on new host4 as well:
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("7", "host4")); // host1:1, host2:2, host3:2, host4:2
        // 8th task fails to launch on hosts2-4 as they now all have more occupancy than host1:
        assertEquals(0, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        assertEquals(0, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("8", "host1")); // host1:2, host2:2, host3:2, host4:2
        // now all hosts1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host4"), REQ, tasks).getResourcesCount());
    }

    private static TaskInfo getTaskInfo(String name, String host) {
        TaskInfo.Builder infoBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
                .setName(name)
                .setTaskId(CommonTaskUtils.toTaskId(name));
        return CommonTaskUtils.setHostname(infoBuilder, offerWithHost(host)).build();
    }

    private static Offer offerWithHost(String host) {
        return OfferTestUtils.getOffer(ResourceTestUtils.getDesiredCpu(1.0)).toBuilder()
                .setHostname(host)
                .build();
    }
}
