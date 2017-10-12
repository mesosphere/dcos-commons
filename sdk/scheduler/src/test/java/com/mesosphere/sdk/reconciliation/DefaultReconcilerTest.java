package com.mesosphere.sdk.reconciliation;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.state.StateStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link DefaultReconciler}.
 */
public class DefaultReconcilerTest {

    private static final Protos.TaskStatus TASK_STATUS_1 = Protos.TaskStatus.newBuilder()
            .setTaskId(Protos.TaskID.newBuilder().setValue("task-1").build())
            .setState(Protos.TaskState.TASK_RUNNING)
            .build();
    private static final Protos.TaskStatus TASK_STATUS_2 = Protos.TaskStatus.newBuilder()
            .setTaskId(Protos.TaskID.newBuilder().setValue("task-2").build())
            .setState(Protos.TaskState.TASK_LOST)
            .build();
    private static final Collection<Protos.TaskStatus> TASK_STATUSES =
            Arrays.asList(TASK_STATUS_1, TASK_STATUS_2);
    private static final long DEFAULT_TIME_MS = 12345L;

    @Mock private SchedulerDriver mockDriver;
    @Mock private StateStore mockStateStore;
    @Captor private ArgumentCaptor<Collection<Protos.TaskStatus>> taskStatusCaptor;

    private TestReconciler reconciler;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        reconciler = new TestReconciler(mockStateStore, DEFAULT_TIME_MS);
    }

    @Test
    public void testStartEmpty() throws Exception {
        assertTrue(reconciler.isReconciled());

        when(mockStateStore.fetchStatuses()).thenReturn(Collections.emptyList());
        reconciler.start();

        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        reconciler.reconcile(mockDriver);

        verify(mockDriver).reconcileTasks(eq(Arrays.asList()));
        assertTrue(reconciler.isReconciled()); // implicit reconciliation has occurred
    }

    @Test
    public void testStart() throws Exception {
        assertTrue(reconciler.isReconciled());
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);

        reconciler.start();

        assertFalse(reconciler.isReconciled());
        assertEquals(2, reconciler.remaining().size());
    }

    @Test
    public void testStartMultipleTimes() throws Exception {
        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        when(mockStateStore.fetchStatuses()).thenReturn(Arrays.asList(TASK_STATUS_1));
        reconciler.start();

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());

        when(mockStateStore.fetchStatuses()).thenReturn(Arrays.asList(TASK_STATUS_2));
        reconciler.start(); // append

        assertFalse(reconciler.isReconciled());
        assertEquals(2, reconciler.remaining().size());

        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        reconciler.start(); // merge

        assertFalse(reconciler.isReconciled());
        assertEquals(2, reconciler.remaining().size());
    }

    @Test
    public void testUpdatesBeforeReconcile() throws Exception {
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        reconciler.start();

        // update() called before requested via reconcile():
        reconciler.update(TASK_STATUS_1);

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_2.getTaskId().getValue(), reconciler.remaining().iterator().next());

        reconciler.update(TASK_STATUS_1); // no change

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_2.getTaskId().getValue(), reconciler.remaining().iterator().next());

        reconciler.update(TASK_STATUS_2);

        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        reconciler.reconcile(mockDriver); // trigger implicit reconciliation
        verify(mockDriver).reconcileTasks(taskStatusCaptor.capture());

        assertEquals(0, taskStatusCaptor.getValue().size());
        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        reconciler.reconcile(mockDriver); // no-op
        verifyNoMoreInteractions(mockDriver);
    }

    @Test
    public void testReconcileSequence() throws Exception {
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        reconciler.start();

        reconciler.reconcile(mockDriver); // first call to reconcileTasks: 2 values

        assertFalse(reconciler.isReconciled());
        assertEquals(2, reconciler.remaining().size());

        reconciler.update(TASK_STATUS_2);

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_1.getTaskId().getValue(), reconciler.remaining().iterator().next());

        reconciler.update(TASK_STATUS_2); // no change

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_1.getTaskId().getValue(), reconciler.remaining().iterator().next());

        // still have a task left, but the time is still the same so driver.reconcile is skipped:
        reconciler.reconcile(mockDriver); // doesn't call reconcileTasks due to timer

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_1.getTaskId().getValue(), reconciler.remaining().iterator().next());

        // bump time forward and try again:
        reconciler.setNowMs(DEFAULT_TIME_MS + 30000);
        reconciler.reconcile(mockDriver); // second call to reconcileTasks: 1 values

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());
        assertEquals(TASK_STATUS_1.getTaskId().getValue(), reconciler.remaining().iterator().next());

        reconciler.update(TASK_STATUS_1);

        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        reconciler.reconcile(mockDriver); // third call to reconcileTasks: 0 values (implicit)

        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());

        reconciler.reconcile(mockDriver); // no-op

        // we need to validate all calls at once due to how mockito deals with Collection calls.
        // otherwise it incorrectly throws "TooManyActualInvocations"
        verify(mockDriver, times(3)).reconcileTasks(taskStatusCaptor.capture());
        List<Collection<Protos.TaskStatus>> allCalls = taskStatusCaptor.getAllValues();
        assertEquals(3, allCalls.size());
        assertEquals(2, allCalls.get(0).size()); // first call (two tasks left)
        assertEquals(1, allCalls.get(1).size()); // second call (one task left)
        assertEquals(0, allCalls.get(2).size()); // third call (implicit)
    }

    @Test
    public void testTaskLostToTaskRunningTransition() throws Exception {
        when(mockStateStore.fetchStatuses()).thenReturn(Arrays.asList(TASK_STATUS_2));
        reconciler.start();

        assertFalse(reconciler.isReconciled());
        assertEquals(1, reconciler.remaining().size());

        reconciler.reconcile(mockDriver);

        final Protos.TaskStatus updatedTaskStatus = Protos.TaskStatus.newBuilder(TASK_STATUS_2)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();

        reconciler.update(updatedTaskStatus);
        reconciler.reconcile(mockDriver);

        assertTrue(reconciler.isReconciled());
        assertEquals(0, reconciler.remaining().size());
        verify(mockDriver, times(2)).reconcileTasks(taskStatusCaptor.capture());

        List<Collection<Protos.TaskStatus>> allCalls = taskStatusCaptor.getAllValues();
        assertEquals(2, allCalls.size());
        assertEquals(1, allCalls.get(0).size());
        assertEquals(0, allCalls.get(1).size());
    }

    /**
     * A DefaultReconciler with adjustable 'now'
     */
    private static class TestReconciler extends DefaultReconciler {
        private long nowMs;

        private TestReconciler(StateStore store, long nowMs) {
            super(store);
            setNowMs(nowMs);
        }

        private void setNowMs(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        protected long getCurrentTimeMillis() {
            return nowMs;
        }
    }
}
