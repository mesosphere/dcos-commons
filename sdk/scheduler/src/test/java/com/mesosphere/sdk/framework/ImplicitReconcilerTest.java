package com.mesosphere.sdk.framework;

import java.util.Collections;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

import static org.mockito.Mockito.*;

public class ImplicitReconcilerTest {

    @Mock private SchedulerConfig mockSchedulerConfig;
    @Mock private SchedulerDriver mockSchedulerDriver;

    private ImplicitReconciler reconciler;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);

        reconciler = new ImplicitReconciler(mockSchedulerConfig);
    }

    @Test
    public void testSingleThread() {
        reconciler.disableThreading().start();
        verify(mockSchedulerDriver).reconcileTasks(Collections.emptyList());
        try {
            reconciler.start();
            Assert.fail("Expected exception from double start");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testScheduledThread() throws InterruptedException {
        when(mockSchedulerConfig.getImplicitReconcileDelayMs()).thenReturn(0L);
        when(mockSchedulerConfig.getImplicitReconcilePeriodMs()).thenReturn(1L);
        reconciler.start();
        Thread.sleep(1000);
        reconciler.stop();
        verify(mockSchedulerDriver, atLeast(2)).reconcileTasks(Collections.emptyList());
    }
}
