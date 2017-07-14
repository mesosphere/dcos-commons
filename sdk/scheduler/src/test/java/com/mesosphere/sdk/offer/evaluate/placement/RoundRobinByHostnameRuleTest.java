package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RoundRobinByHostnameRule}.
 */
public class RoundRobinByHostnameRuleTest extends DefaultCapabilitiesTestSuite {
    private static final StringMatcher MATCHER = RegexMatcher.create("[0-9]");
    private static PodInstance POD;

    @BeforeClass
    public static void beforeAll() throws InvalidRequirementException {
        POD = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance();
    }

    private static TaskInfo getTaskInfo(String name, String host) {
        TaskInfo.Builder infoBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
                .setName(name)
                .setTaskId(CommonIdUtils.toTaskId(name));
        infoBuilder.setLabels(new TaskLabelWriter(infoBuilder).setHostname(offerWithHost(host)).toProto());
        return infoBuilder.build();
    }

    private static Offer offerWithHost(String host) {
        return OfferTestUtils.getCompleteOffer(
                ResourceBuilder.fromUnreservedValue(
                        "cpus",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
                                .build())
                        .build())
                .toBuilder()
                .setHostname(host)
                .build();
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
        PlacementRule rule = new RoundRobinByHostnameRule(Optional.of(3), MATCHER);
        // throw in some preexisting tasks to be ignored by our matcher:
        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(getTaskInfo("ignored1", "host1"));
        tasks.add(getTaskInfo("ignored2", "host2"));
        tasks.add(getTaskInfo("ignored3", "host3"));
        tasks.add(getTaskInfo("ignored4", "host1"));
        tasks.add(getTaskInfo("ignored5", "host2"));
        // 1st task fits on host1:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        TaskInfo taskInfo1 = getTaskInfo("1", "host1");
        PodInstance req1 = getPodInstance(taskInfo1);
        tasks.add(taskInfo1); // host1:1
        // 2nd task doesn't fit on host1 which already has something, but does fit on host2/host3:
        assertFalse(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        TaskInfo taskInfo2 = getTaskInfo("2", "host3");
        PodInstance req2 = getPodInstance(taskInfo2);
        tasks.add(taskInfo2); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertTrue(rule.filter(offerWithHost("host1"), req1, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), req2, tasks).isPassing());
        // 3rd task doesnt fit on host1/host3, does fit on host2:
        assertFalse(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("3", "host2")); // host1:1, host2:1, host3:1
        // 4th task fits on any host as the three hosts now have the same size:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("4", "host2")); // host1:1, host2:2, host3:1
        // 5th task doesn't fit on host2 but does fit on host1/host3:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("5", "host3")); // host1:1, host2:2, host3:2
        // 6th task is launched on erroneous host4 (host4 shouldn't exist: we were told there were only 3 hosts!)
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("6", "host4")); // host1:1, host2:2, host3:2, host4:1
        // 7th task is launched on host4 as well:
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("7", "host4")); // host1:1, host2:2, host3:2, host4:2
        // 8th task fails to launch on hosts2-4 as they now all have more occupancy than host1:
        assertFalse(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("8", "host1")); // host1:2, host2:2, host3:2, host4:2
        // now all hosts1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
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
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        TaskInfo taskInfo1 = getTaskInfo("1", "host1");
        PodInstance req1 = getPodInstance(taskInfo1);
        tasks.add(taskInfo1); // host1:1
        // 2nd task fits on any of host1/host2/host3, as we don't yet know of other valid hosts:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        TaskInfo taskInfo2 = getTaskInfo("2", "host3");
        PodInstance req2 = getPodInstance(taskInfo2);
        tasks.add(taskInfo2); // host1:1, host3:1
        // duplicates of preexisting tasks 1/3 fit on their previous hosts:
        assertTrue(rule.filter(offerWithHost("host1"), req1, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), req2, tasks).isPassing());
        // 3rd task fits on any of host1/host2/host3, as all known hosts have the same amount:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("3", "host2")); // host1:1, host2:1, host3:1
        // 4th task fits on any of host1/host2/host3, as all known hosts have the same amount:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("4", "host2")); // host1:1, host2:2, host3:1
        // 5th task doesn't fit on host2 but does fit on host1/host3:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("5", "host3")); // host1:1, host2:2, host3:2
        // 6th task is launched on new host4:
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("6", "host4")); // host1:1, host2:2, host3:2, host4:1
        // 7th task is launched on new host4 as well:
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("7", "host4")); // host1:1, host2:2, host3:2, host4:2
        // 8th task fails to launch on hosts2-4 as they now all have more occupancy than host1:
        assertFalse(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        assertFalse(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        tasks.add(getTaskInfo("8", "host1")); // host1:2, host2:2, host3:2, host4:2
        // now all hosts1-4 have equal occupancy = 2. adding a task to any of them should work:
        assertTrue(rule.filter(offerWithHost("host1"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host2"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host3"), POD, tasks).isPassing());
        assertTrue(rule.filter(offerWithHost("host4"), POD, tasks).isPassing());
    }
}
