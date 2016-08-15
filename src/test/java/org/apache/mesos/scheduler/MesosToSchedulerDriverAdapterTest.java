package org.apache.mesos.scheduler;

import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.v1.scheduler.Mesos;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class MesosToSchedulerDriverAdapterTest {
    private static final FrameworkInfo FRAMEWORK_INFO = FrameworkInfo
            .newBuilder()
            .setUser("Foo")
            .setName("Bar")
            .build();

    @Test
    public void testStart() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
    }

    @Test
    public void testAbort() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_ABORTED, driver.abort());
    }

    @Test
    public void testDisconnected() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        driver.disconnected(mesos);

        verify(scheduler, times(1)).disconnected(driver);
    }

    private static class CustomMesosToSchedulerDriverAdapter extends MesosToSchedulerDriverAdapter {
        private final Mesos mesos;
        CustomMesosToSchedulerDriverAdapter(Scheduler scheduler, Mesos mesos) {
            super(scheduler, FRAMEWORK_INFO, "master");
            this.mesos = mesos;
        }

        @Override
        protected Mesos startInternal() {
            return mesos;
        }
    }
}
