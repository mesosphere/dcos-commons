package org.apache.mesos.scheduler.plan;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.reconciliation.Reconciler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Sets;

/**
 * Tests for {@link ReconciliationBlock}.
 */
public class ReconciliationBlockTest {

    private static final Set<TaskStatus> STATUSES = Sets.newHashSet(
            createTaskStatus("status1"), createTaskStatus("status2"));

    @Mock private Reconciler mockReconciler;

    private ReconciliationBlock block;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        block = ReconciliationBlock.create(mockReconciler);
    }

    @Test
    public void testStartsPending() {
        assertTrue(block.isPending());
        assertTrue(block.getMessage().contains("pending"));
        verifyZeroInteractions(mockReconciler);
    }

    @Test
    public void testStart_statusProviderFailure() throws Exception {
        doThrow(new RuntimeException("hello")).when(mockReconciler).start();
        assertNull(block.start());
        assertTrue(block.isPending());
    }

    @Test
    public void testStart() throws Exception {
        assertNull(block.start());
        assertFalse(block.isPending());

        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(block.isInProgress());
        assertTrue(block.getMessage().contains("in progress"));
        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(block.isComplete());
        assertTrue(block.getMessage().contains("complete"));
    }

    @Test
    public void testStartInProgressRestart() throws Exception {
        assertNull(block.start());

        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(block.isInProgress());
        block.restart();
        assertTrue(block.isPending());
    }

    @Test
    public void testStartCompleteRestart() throws Exception {
        assertNull(block.start());
        assertFalse(block.isPending());

        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(block.isComplete());
        block.restart();
        assertTrue(block.isPending());
    }

    @Test
    public void testForceCompleteFromPending() {
        assertTrue(block.isPending());

        block.forceComplete();
        when(mockReconciler.isReconciled()).thenReturn(true); // simulate reconciler now complete
        assertTrue(block.isComplete());
    }

    @Test
    public void testForceCompleteFromInProgress() throws Exception {
        assertNull(block.start());
        when(mockReconciler.isReconciled()).thenReturn(false);
        assertTrue(block.isInProgress());

        block.forceComplete();
        when(mockReconciler.isReconciled()).thenReturn(true); // simulate reconciler now complete
        assertTrue(block.isComplete());
    }

    @Test
    public void testForceCompleteFromComplete() throws Exception {
        assertNull(block.start());
        when(mockReconciler.isReconciled()).thenReturn(true);
        assertTrue(block.isComplete());

        block.forceComplete();
        assertTrue(block.isComplete());
    }

    @Test
    public void testUpdateOfferStatusFalseSucceeds() {
        // Expect no exception to be thrown
        block.updateOfferStatus(false);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testUpdateOfferStatusTrueFails() {
        block.updateOfferStatus(true);
    }

    @Test
    public void testUpdate() {
        block.update(STATUSES.iterator().next());
    }

    private static TaskStatus createTaskStatus(String id) {
        TaskStatus.Builder builder = TaskStatus.newBuilder().setState(TaskState.TASK_RUNNING);
        builder.getTaskIdBuilder().setValue(id);
        return builder.build();
    }
}
