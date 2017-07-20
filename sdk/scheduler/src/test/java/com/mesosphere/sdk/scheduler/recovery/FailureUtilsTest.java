package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link FailureUtils}.
 */
public class FailureUtilsTest {

    private static final Protos.TaskInfo TASK0_NOTFAILED = TaskTestUtils.getTaskInfo(Collections.emptyList(), 0);
    private static final Protos.TaskInfo TASK1_NOTFAILED = TaskTestUtils.getTaskInfo(Collections.emptyList(), 1);
    private static final Protos.TaskInfo TASK2_NOTFAILED = TaskTestUtils.getTaskInfo(Collections.emptyList(), 2);

    private static final Protos.TaskInfo TASK0_FAILED = TASK0_NOTFAILED.toBuilder()
            .setLabels(new TaskLabelWriter(TASK0_NOTFAILED).setPermanentlyFailed().toProto())
            .build();
    private static final Protos.TaskInfo TASK1_FAILED = TASK1_NOTFAILED.toBuilder()
            .setLabels(new TaskLabelWriter(TASK1_NOTFAILED).setPermanentlyFailed().toProto())
            .build();
    private static final Protos.TaskInfo TASK2_FAILED = TASK2_NOTFAILED.toBuilder()
            .setLabels(new TaskLabelWriter(TASK2_NOTFAILED).setPermanentlyFailed().toProto())
            .build();

    @Mock StateStore mockStateStore;
    @Mock PodInstance mockPodInstance;
    @Mock PodSpec mockPodSpec;
    @Mock TaskSpec mockTaskSpec0;
    @Mock TaskSpec mockTaskSpec1;
    @Mock TaskSpec mockTaskSpec2;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFailureMarking_NoneLaunched() {
        when(mockPodInstance.getName()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);

        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0, mockTaskSpec1, mockTaskSpec2));
        when(mockTaskSpec0.getName()).thenReturn(TestConstants.TASK_NAME + "0");
        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "1");
        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "2");

        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "0"))
                .thenReturn(Optional.empty());
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "1"))
                .thenReturn(Optional.empty());
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "2"))
                .thenReturn(Optional.empty());

        Assert.assertFalse(FailureUtils.isAllMarkedFailed(mockStateStore, mockPodInstance));
    }

    @Test
    public void testFailureMarking_SomeLaunched_AllFailed() {
        when(mockPodInstance.getName()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);

        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0, mockTaskSpec1, mockTaskSpec2));
        when(mockTaskSpec0.getName()).thenReturn(TestConstants.TASK_NAME + "0");
        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "1");
        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "2");

        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "0"))
                .thenReturn(Optional.of(TASK0_FAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "1"))
                .thenReturn(Optional.empty());
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "2"))
                .thenReturn(Optional.of(TASK2_FAILED));

        Assert.assertTrue(FailureUtils.isAllMarkedFailed(mockStateStore, mockPodInstance));
    }

    @Test
    public void testFailureMarking_SomeLaunched_SomeFailed() {
        when(mockPodInstance.getName()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);

        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0, mockTaskSpec1, mockTaskSpec2));
        when(mockTaskSpec0.getName()).thenReturn(TestConstants.TASK_NAME + "0");
        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "1");
        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "2");

        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "0"))
                .thenReturn(Optional.of(TASK0_FAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "1"))
                .thenReturn(Optional.empty());
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "2"))
                .thenReturn(Optional.of(TASK2_NOTFAILED));

        Assert.assertFalse(FailureUtils.isAllMarkedFailed(mockStateStore, mockPodInstance));
    }

    @Test
    public void testFailureMarking_AllLaunched_AllFailed() {
        when(mockPodInstance.getName()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);

        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0, mockTaskSpec1, mockTaskSpec2));
        when(mockTaskSpec0.getName()).thenReturn(TestConstants.TASK_NAME + "0");
        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "1");
        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "2");

        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "0"))
                .thenReturn(Optional.of(TASK0_FAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "1"))
                .thenReturn(Optional.of(TASK1_FAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "2"))
                .thenReturn(Optional.of(TASK2_FAILED));

        Assert.assertTrue(FailureUtils.isAllMarkedFailed(mockStateStore, mockPodInstance));
    }

    @Test
    public void testFailureMarking_AllLaunched_SomeFailed() {
        when(mockPodInstance.getName()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);

        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0, mockTaskSpec1, mockTaskSpec2));
        when(mockTaskSpec0.getName()).thenReturn(TestConstants.TASK_NAME + "0");
        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "1");
        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "2");

        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "0"))
                .thenReturn(Optional.of(TASK0_FAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "1"))
                .thenReturn(Optional.of(TASK1_NOTFAILED));
        when(mockStateStore.fetchTask(TestConstants.POD_TYPE + "-" + TestConstants.TASK_NAME + "2"))
                .thenReturn(Optional.of(TASK2_FAILED));

        Assert.assertFalse(FailureUtils.isAllMarkedFailed(mockStateStore, mockPodInstance));
    }
}
