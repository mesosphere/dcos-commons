package com.mesosphere.sdk.scheduler.decommission;

import java.util.Arrays;
import java.util.Collections;

import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.DestroyOfferRecommendation;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link DecommissionRecorder}.
 */
public class DecommissionRecorderTest {

    @Mock private Capabilities mockCapabilities;
	@Mock private StateStore mockStateStore;
	@Mock private Step mockStep;

	private DecommissionRecorder recorder;

	private Protos.Resource taskResource;
	private Protos.Resource otherResource;

	private Protos.TaskInfo taskA;
	private Protos.TaskInfo taskB;
	private Protos.TaskInfo emptyTaskA;
	private Protos.TaskInfo emptyTaskB;

	private Protos.TaskInfo emptyTask;

	@Before
	public void beforeEach() {
		MockitoAnnotations.initMocks(this);
        Capabilities.overrideCapabilities(mockCapabilities);
        recorder = new DecommissionRecorder(mockStateStore, Arrays.asList(mockStep));

        taskResource = ResourceTestUtils.getReservedCpus(5, "matching-resource");
        otherResource = ResourceTestUtils.getReservedCpus(5, "other-resource");

        taskA = TaskTestUtils.getTaskInfo(taskResource);
        taskB = TaskTestUtils.getTaskInfo(taskResource);
        emptyTaskA = taskA.toBuilder().clearResources().build();
        emptyTaskB = taskB.toBuilder().clearResources().build();

        emptyTask = TaskTestUtils.getTaskInfo(Collections.emptyList());

		when(mockStateStore.fetchTasks()).thenReturn(Arrays.asList(taskA, taskB, emptyTask));
	}

	@Test
	public void testDestroyNotFound() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
		recorder.record(new DestroyOfferRecommendation(null, otherResource));
		verify(mockStateStore, times(0)).storeTasks(any());
	}

	@Test
	public void testUnreserveNotFound() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
		recorder.record(new UnreserveOfferRecommendation(null, otherResource));
		verify(mockStateStore, times(0)).storeTasks(any());
	}

	@Test
	public void testDestroyNotDecommissioning() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(GoalStateOverride.Status.INACTIVE);
		recorder.record(new DestroyOfferRecommendation(null, taskResource));
		verify(mockStateStore, times(0)).storeTasks(any());
	}

	@Test
	public void testUnreserveNotDecommissioning() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(GoalStateOverride.Status.INACTIVE);
		recorder.record(new UnreserveOfferRecommendation(null, taskResource));
		verify(mockStateStore, times(0)).storeTasks(any());
	}

	@Test
	public void testDestroy() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
		recorder.record(new DestroyOfferRecommendation(null, taskResource));
		verify(mockStateStore).storeTasks(Arrays.asList(emptyTaskA, emptyTaskB));
	}

	@Test
	public void testUnreserve() throws Exception {
		when(mockStateStore.fetchGoalOverrideStatus(TestConstants.TASK_NAME))
				.thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
		recorder.record(new UnreserveOfferRecommendation(null, taskResource));
		verify(mockStateStore).storeTasks(Arrays.asList(emptyTaskA, emptyTaskB));
	}
}
