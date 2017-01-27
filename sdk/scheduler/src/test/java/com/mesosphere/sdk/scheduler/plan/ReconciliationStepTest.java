package com.mesosphere.sdk.scheduler.plan;

import com.google.common.collect.Sets;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReconciliationStep}.
 */
public class ReconciliationStepTest {

    private static final Set<TaskStatus> STATUSES = Sets.newHashSet(
            createTaskStatus("status1"), createTaskStatus("status2"));

    @Mock private Reconciler mockReconciler;

    private ReconciliationStep step;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        step = ReconciliationStep.create(mockReconciler);
    }

    @Test
    public void testStartsPending() {
        assertTrue(step.isPending());
        assertTrue(step.getMessage().contains("pending"));
    }

    @Test
    public void testStartStatusProviderFailure() throws Exception {
        doThrow(new RuntimeException("hello")).when(mockReconciler).start();
        assertFalse(step.start().isPresent());
        assertTrue(step.isPending());
    }

    @Test
    public void testStart() throws Exception {
        assertFalse(step.start().isPresent());
        assertFalse(step.isPending());

        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(step.isPrepared());
        assertTrue(step.getMessage().contains("in progress"));
        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(step.isComplete());
        assertTrue(step.getMessage().contains("complete"));
    }

    @Test
    public void testStartInProgressRestart() throws Exception {
        assertFalse(step.start().isPresent());

        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(step.isPrepared());
        step.restart();
        assertTrue(step.isPending());
    }

    @Test
    public void testStartCompleteRestart() throws Exception {
        assertFalse(step.start().isPresent());
        assertFalse(step.isPending());

        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(step.isComplete());
        step.restart();
        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(step.isPending());
    }

    @Test
    public void testForceCompleteFromPending() {
        assertTrue(step.isPending());

        step.forceComplete();
        when(mockReconciler.isReconciled()).thenReturn(true); // simulate reconciler now complete
        assertTrue(step.isComplete());
    }

    @Test
    public void testForceCompleteFromInProgress() throws Exception {
        assertFalse(step.start().isPresent());
        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(step.isPrepared());

        step.forceComplete();
        when(mockReconciler.isReconciled()).thenReturn(true); // simulate reconciler now complete
        assertTrue(step.isComplete());
    }

    @Test
    public void testForceCompleteFromComplete() throws Exception {
        assertFalse(step.start().isPresent());
        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(step.isComplete());

        step.forceComplete();
        assertTrue(step.isComplete());
    }

    @Test
    public void testUpdateOfferStatusFalseSucceeds() {
        // Expect no exception to be thrown
        step.updateOfferStatus(Collections.emptyList());
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testUpdateOfferStatusTrueFails() {
        LaunchOfferRecommendation launchRec = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                TaskTestUtils.getTaskInfo(Collections.emptyList()));
        step.updateOfferStatus(Arrays.asList(launchRec));
    }

    @Test
    public void testUpdate() {
        step.update(STATUSES.iterator().next());
    }

    private static TaskStatus createTaskStatus(String id) {
        TaskStatus.Builder builder = TaskStatus.newBuilder().setState(TaskState.TASK_RUNNING);
        builder.getTaskIdBuilder().setValue(id);
        return builder.build();
    }
}
