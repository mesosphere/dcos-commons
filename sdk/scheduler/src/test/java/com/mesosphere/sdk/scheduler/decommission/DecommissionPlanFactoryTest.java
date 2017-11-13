package com.mesosphere.sdk.scheduler.decommission;

import java.util.Arrays;
import java.util.Collection;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory.PodKey;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link DecommissionPlanFactory}.
 */
public class DecommissionPlanFactoryTest {

    private static final String POD_TYPE_A = "podA";
    private static final String POD_TYPE_B = "podB";
    private static final String POD_TYPE_C = "podC";
    private static final String POD_TYPE_D = "podD";
    private static final String POD_TYPE_E = "podE";

    private static final String TASK_A = "taskA";
    private static final String TASK_B = "taskB";

    private Collection<Protos.TaskInfo> tasks;

    @Mock private Capabilities mockCapabilities;
    @Mock private PodSpec mockPodSpecA;
    @Mock private PodSpec mockPodSpecB;
    @Mock private PodSpec mockPodSpecC;
    @Mock private PodSpec mockPodSpecD;
    @Mock private PodSpec mockPodSpecE;
    @Mock private ServiceSpec mockServiceSpec;
    @Mock private StateStore mockStateStore;
    @Mock private TaskKiller mockTaskKiller;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Capabilities.overrideCapabilities(mockCapabilities);

        tasks = Arrays.asList(
                // podA has 3 pods (each with 1 task), but count is now 1
                getTask(POD_TYPE_A, 0, TASK_A, 1), // decommissioned, pending
                getTask(POD_TYPE_A, 1, TASK_A, 2), // decommissioned, inprogress
                getTask(POD_TYPE_A, 2, TASK_A, 3), // inactive
                // pod B has 2 pods (each with 2 tasks), but count is now 1
                getTask(POD_TYPE_B, 0, TASK_A, 1), // paused, complete
                getTask(POD_TYPE_B, 0, TASK_B, 2), // decommissioned, pending
                getTask(POD_TYPE_B, 1, TASK_A, 1), // decommissioned, inprogress
                getTask(POD_TYPE_B, 1, TASK_B, 2), // inactive
                // pod C has 1 pod, which matches the count
                getTask(POD_TYPE_C, 0, TASK_A, 3), // paused, complete
                // pods D and E are not listed
                getTask(POD_TYPE_D, 0, TASK_A, 3), // decommissioned, pending
                getTask(POD_TYPE_D, 1, TASK_A, 1), // decommissioned, inprogress
                getTask(POD_TYPE_E, 0, TASK_A, 2)); // inactive

        when(mockPodSpecA.getType()).thenReturn(POD_TYPE_A);
        when(mockPodSpecA.getCount()).thenReturn(1);
        when(mockPodSpecB.getType()).thenReturn(POD_TYPE_B);
        when(mockPodSpecB.getCount()).thenReturn(1);
        when(mockPodSpecC.getType()).thenReturn(POD_TYPE_C);
        when(mockPodSpecC.getCount()).thenReturn(1);
        when(mockServiceSpec.getPods()).thenReturn(Arrays.asList(mockPodSpecA, mockPodSpecB, mockPodSpecC));

        // Iterate over tasks and set various preexisting override states.
        // They should be cleared or updated automatically depending on the plan result (verified below):
        int i = 0;
        for (Protos.TaskInfo task : tasks) {
            GoalStateOverride.Status status;
            switch (i % 4) {
            case 0:
                status = GoalStateOverride.DECOMMISSIONED.newStatus(GoalStateOverride.Progress.PENDING);
                break;
            case 1:
                status = GoalStateOverride.DECOMMISSIONED.newStatus(GoalStateOverride.Progress.IN_PROGRESS);
                break;
            case 2:
                status = GoalStateOverride.Status.INACTIVE;
                break;
            case 3:
                status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.COMPLETE);
                break;
            default:
                throw new IllegalStateException();
            }
            when(mockStateStore.fetchGoalOverrideStatus(task.getName())).thenReturn(status);
            i++;
        }
    }

    @Test
    public void testNoDecommission() {
        when(mockStateStore.fetchTasks()).thenReturn(tasks);
        // when spec's pods are equal to (or greater than) the number of launched pods, no decommission should be necessary:
        when(mockPodSpecA.getCount()).thenReturn(3);
        when(mockPodSpecB.getCount()).thenReturn(100);
        when(mockPodSpecC.getCount()).thenReturn(1);
        when(mockPodSpecD.getType()).thenReturn(POD_TYPE_D);
        when(mockPodSpecD.getCount()).thenReturn(2);
        when(mockPodSpecE.getType()).thenReturn(POD_TYPE_E);
        when(mockPodSpecE.getCount()).thenReturn(2);
        when(mockServiceSpec.getPods()).thenReturn(
                Arrays.asList(mockPodSpecA, mockPodSpecB, mockPodSpecC, mockPodSpecD, mockPodSpecE));
        DecommissionPlanFactory factory = new DecommissionPlanFactory(mockServiceSpec, mockStateStore, mockTaskKiller);
        Assert.assertFalse(factory.getPlan().isPresent());
        Assert.assertTrue(factory.getResourceSteps().isEmpty());

        // any tasks that with existing decommission bits should have had their decommission bits cleared (see list above):
        for (String taskToClear : Arrays.asList(
                "podA-0-taskA",
                "podA-1-taskA",
                "podB-0-taskB",
                "podB-1-taskA",
                "podD-0-taskA",
                "podD-1-taskA")) {
            verify(mockStateStore, times(1)).storeGoalOverrideStatus(taskToClear, GoalStateOverride.Status.INACTIVE);
        }
        verify(mockStateStore, times(0)).storeGoalOverrideStatus(anyString(), eq(DecommissionPlanFactory.DECOMMISSIONING_STATUS));
    }

    @Test
    public void testBigPlanConstruction() {
        when(mockStateStore.fetchTasks()).thenReturn(tasks);
        DecommissionPlanFactory factory = new DecommissionPlanFactory(mockServiceSpec, mockStateStore, mockTaskKiller);

        // any tasks with existing decommission bits but which are not to be decommissioned have had their decommission bits cleared (see list above):
        for (String taskToClear : Arrays.asList("podA-0-taskA", "podB-0-taskB")) {
            verify(mockStateStore, times(1)).storeGoalOverrideStatus(taskToClear, GoalStateOverride.Status.INACTIVE);
        }
        // any tasks to decommission that were paused or no-override are assigned with a pending decommission bit:
        for (String taskToClear : Arrays.asList("podA-2-taskA", "podB-1-taskB", "podE-0-taskA")) {
            verify(mockStateStore, times(1)).storeGoalOverrideStatus(taskToClear,
                    GoalStateOverride.DECOMMISSIONED.newStatus(GoalStateOverride.Progress.PENDING));
        }

        Assert.assertEquals(14, factory.getResourceSteps().size());

        Assert.assertTrue(factory.getPlan().isPresent());
        Plan plan = factory.getPlan().get();
        Assert.assertEquals(6, plan.getChildren().size());
        Assert.assertEquals(Status.PENDING, plan.getStatus());

        Phase phase = plan.getChildren().get(0);
        Assert.assertEquals("podD-1", phase.getName());
        Assert.assertEquals(3, phase.getChildren().size());
        Assert.assertEquals("kill-podD-1-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-podD-1-taskA-resource0", phase.getChildren().get(1).getName());
        Assert.assertEquals("erase-podD-1-taskA", phase.getChildren().get(2).getName());

        phase = plan.getChildren().get(1);
        Assert.assertEquals("podD-0", phase.getName());
        Assert.assertEquals(5, phase.getChildren().size());
        Assert.assertEquals("kill-podD-0-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-podD-0-taskA-resource0", phase.getChildren().get(1).getName());
        Assert.assertEquals("unreserve-podD-0-taskA-resource1", phase.getChildren().get(2).getName());
        Assert.assertEquals("unreserve-podD-0-taskA-resource2", phase.getChildren().get(3).getName());
        Assert.assertEquals("erase-podD-0-taskA", phase.getChildren().get(4).getName());

        phase = plan.getChildren().get(2);
        Assert.assertEquals("podE-0", phase.getName());
        Assert.assertEquals(4, phase.getChildren().size());
        Assert.assertEquals("kill-podE-0-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-podE-0-taskA-resource0", phase.getChildren().get(1).getName());
        Assert.assertEquals("unreserve-podE-0-taskA-resource1", phase.getChildren().get(2).getName());
        Assert.assertEquals("erase-podE-0-taskA", phase.getChildren().get(3).getName());

        phase = plan.getChildren().get(3);
        Assert.assertEquals("podB-1", phase.getName());
        Assert.assertEquals(7, phase.getChildren().size());
        Assert.assertEquals("kill-podB-1-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("kill-podB-1-taskB", phase.getChildren().get(1).getName());
        Assert.assertEquals("unreserve-podB-1-taskA-resource0", phase.getChildren().get(2).getName());
        Assert.assertEquals("unreserve-podB-1-taskB-resource0", phase.getChildren().get(3).getName());
        Assert.assertEquals("unreserve-podB-1-taskB-resource1", phase.getChildren().get(4).getName());
        Assert.assertEquals("erase-podB-1-taskA", phase.getChildren().get(5).getName());
        Assert.assertEquals("erase-podB-1-taskB", phase.getChildren().get(6).getName());

        phase = plan.getChildren().get(4);
        Assert.assertEquals("podA-2", phase.getName());
        Assert.assertEquals(5, phase.getChildren().size());
        Assert.assertEquals("kill-podA-2-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-podA-2-taskA-resource0", phase.getChildren().get(1).getName());
        Assert.assertEquals("unreserve-podA-2-taskA-resource1", phase.getChildren().get(2).getName());
        Assert.assertEquals("unreserve-podA-2-taskA-resource2", phase.getChildren().get(3).getName());
        Assert.assertEquals("erase-podA-2-taskA", phase.getChildren().get(4).getName());

        phase = plan.getChildren().get(5);
        Assert.assertEquals("podA-1", phase.getName());
        Assert.assertEquals(4, phase.getChildren().size());
        Assert.assertEquals("kill-podA-1-taskA", phase.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-podA-1-taskA-resource0", phase.getChildren().get(1).getName());
        Assert.assertEquals("unreserve-podA-1-taskA-resource1", phase.getChildren().get(2).getName());
        Assert.assertEquals("erase-podA-1-taskA", phase.getChildren().get(3).getName());
    }

    @Test
    public void testPodOrdering() {
        SortedMap<PodKey, Collection<Protos.TaskInfo>> podsToDecommission =
                DecommissionPlanFactory.getPodsToDecommission(mockServiceSpec, tasks);
        Assert.assertEquals(Arrays.asList("podD-1", "podD-0", "podE-0", "podB-1", "podA-2", "podA-1"),
                podsToDecommission.keySet().stream().map(PodKey::getPodName).collect(Collectors.toList()));
    }

    private static Protos.TaskInfo getTask(
            String podType, int podIndex, String taskName, int resourceCount) {
        String fullTaskName = String.format("%s-%s", PodInstance.getName(podType, podIndex), taskName);
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setName(fullTaskName)
                .setSlaveId(TestConstants.AGENT_ID);
        builder.setLabels(new TaskLabelWriter(builder)
                .setType(podType)
                .setIndex(podIndex)
                .toProto());
        for (int i = 0; i < resourceCount; ++i) {
            builder.addResources(ResourceTestUtils.getReservedCpus(5, String.format("%s-resource%d", fullTaskName, i)));
        }
        return builder.build();
    }
}
