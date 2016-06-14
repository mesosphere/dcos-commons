package org.apache.mesos.reconciliation;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;

/**
 * This class tests the DefaultReconciler implementation.
 */
public class ReconcilerTest {

    private static String testTaskId = "test-task-id";
    private Protos.TaskStatus testTaskStatus;
    private Reconciler reconciler;

    @Mock
    private SchedulerDriver driver;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);

        testTaskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(
                    Protos.TaskID.newBuilder()
                    .setValue(testTaskId)
                    .build())
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();

        reconciler = new DefaultReconciler();
    }

    @Test
    public void testReconcilerStart() {
        reconciler.start(getTestTaskStatuses());
        Assert.assertFalse(reconciler.isReconciled());
        Assert.assertEquals(1, reconciler.remaining().size());
    }

    @Test
    public void testReconcilerStartUpdateReconcile() {
        reconciler.start(getTestTaskStatuses());
        reconciler.update(testTaskStatus);
        Assert.assertFalse(reconciler.isReconciled());
        reconciler.reconcile(driver);
        Assert.assertTrue(reconciler.isReconciled());
        Assert.assertEquals(0, reconciler.remaining().size());
    }

    @Test
    public void testForceCompleteReconciler() {
        reconciler.start(getTestTaskStatuses());
        Assert.assertFalse(reconciler.isReconciled());
        reconciler.forceComplete();
        Assert.assertTrue(reconciler.isReconciled());
    }

    private Collection<Protos.TaskStatus> getTestTaskStatuses() {
        return Arrays.asList(testTaskStatus);
    }
}
