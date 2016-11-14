package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;

/**
 * Tests for {@link RoundRobinByHostnameRule}.
 */
public class RoundRobinByHostnameRuleTest {

    private static final OfferRequirement REQ;
    private static final OfferRequirement REQ1;
    private static final OfferRequirement REQ2;
    private static final StringMatcher MATCHER = RegexMatcher.create("[0-9]");
    static {
        OfferRequirement req = null, req1 = null, req2 = null;
        try {
            req = OfferRequirement.create(TestConstants.TASK_TYPE, Collections.emptyList());
            req1 = OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(getTaskInfo("1", "host")));
            req2 = OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(getTaskInfo("2", "host")));
        } catch (InvalidRequirementException e) {
            fail(e.toString());
        }
        REQ = req;
        REQ1 = req1;
        REQ2 = req2;
    }

    @Test
    public void testRolloutWithAgentCount() {
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
        tasks.add(getTaskInfo("1", "host1")); // host1:1
        // 2nd task doesn't fit on host1 which already has something, but does fit on host2/host3:
        assertEquals(0, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("2", "host3")); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ2, tasks).getResourcesCount());
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
    public void testRolloutWithoutAgentCount() {
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
        tasks.add(getTaskInfo("1", "host1")); // host1:1
        // 2nd task fits on any of host1/host2/host3, as we don't yet know of other valid hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host2"), REQ, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ, tasks).getResourcesCount());
        tasks.add(getTaskInfo("2", "host3")); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertEquals(1, rule.filter(offerWithHost("host1"), REQ1, tasks).getResourcesCount());
        assertEquals(1, rule.filter(offerWithHost("host3"), REQ2, tasks).getResourcesCount());
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
                .setTaskId(TaskUtils.toTaskId(name));
        return TaskUtils.setHostname(infoBuilder, offerWithHost(host)).build();
    }

    private static Offer offerWithHost(String host) {
        return OfferTestUtils.getOffer(ResourceTestUtils.getDesiredCpu(1.0)).toBuilder()
                .setHostname(host)
                .build();
    }
}
