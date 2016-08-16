package org.apache.mesos.scheduler;

import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

import java.util.Timer;
import java.util.TimerTask;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MesosToSchedulerDriverAdapterTest {
    private static final FrameworkInfo FRAMEWORK_INFO = FrameworkInfo
            .newBuilder()
            .setUser("Foo")
            .setName("Bar")
            .build();

    @Test
    public void testStart() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
    }

    @Test
    public void testAbort() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_ABORTED, driver.abort());
    }

    @Test
    public void testDisconnected() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        driver.disconnected(mesos);

        verify(scheduler, times(1)).disconnected(driver);
    }

    @Test
    public void testSubscribe() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);
        Timer subscribeTimer = mock(Timer.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        doAnswer((InvocationOnMock invocation) -> {
            TimerTask callback = (TimerTask) invocation.getArguments()[0];
            callback.run();
            final Protos.Event subscribedEvent = org.apache.mesos.v1.scheduler.Protos.Event.newBuilder()
                    .setSubscribed(org.apache.mesos.v1.scheduler.Protos.Event.Subscribed.newBuilder()
                            .setFrameworkId(org.apache.mesos.v1.Protos.FrameworkID.newBuilder()
                                    .setValue("dummy-framework")
                                    .build())
                            .setHeartbeatIntervalSeconds(0)
                            .build())
                    .setType(Protos.Event.Type.SUBSCRIBED)
                    .build();
            driver.received(mesos, subscribedEvent);
            return null;
        }).doNothing().when(subscribeTimer).schedule(any(TimerTask.class), anyLong());

        driver.connected(mesos);

        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        final Protos.Call value = callCaptor.getValue();
        assertEquals(value.getType(), Protos.Call.Type.SUBSCRIBE);
    }

    @Test
    public void testSubscribeDisconnect() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);
        Timer subscribeTimer = mock(Timer.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        doAnswer((InvocationOnMock invocation) -> {
            TimerTask callback = (TimerTask) invocation.getArguments()[0];
            callback.run();
            final Protos.Event subscribedEvent = org.apache.mesos.v1.scheduler.Protos.Event.newBuilder()
                    .setSubscribed(org.apache.mesos.v1.scheduler.Protos.Event.Subscribed.newBuilder()
                            .setFrameworkId(org.apache.mesos.v1.Protos.FrameworkID.newBuilder()
                                    .setValue("dummy-framework")
                                    .build())
                            .setHeartbeatIntervalSeconds(0)
                            .build())
                    .setType(Protos.Event.Type.SUBSCRIBED)
                    .build();
            driver.received(mesos, subscribedEvent);

            driver.disconnected(mesos);
            return null;
        }).doNothing().when(subscribeTimer).schedule(any(TimerTask.class), anyLong());

        driver.connected(mesos);

        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);

        /*
         mesos.send() should not be invoked by subscribe timer, after we get disconnect.
         */
        verify(mesos, times(1)).send(callCaptor.capture());
        final Protos.Call value = callCaptor.getValue();
        assertEquals(value.getType(), Protos.Call.Type.SUBSCRIBE);
    }

    @Test
    public void testSubscribeBackoff() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);
        Timer subscribeTimer = mock(Timer.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        doAnswer((InvocationOnMock invocation) -> {
            TimerTask callback = (TimerTask) invocation.getArguments()[0];
            callback.run();
            return null;
        }).doAnswer((InvocationOnMock invocation) -> {
            TimerTask callback = (TimerTask) invocation.getArguments()[0];
            callback.run();
            final Protos.Event subscribedEvent = org.apache.mesos.v1.scheduler.Protos.Event.newBuilder()
                    .setSubscribed(org.apache.mesos.v1.scheduler.Protos.Event.Subscribed.newBuilder()
                            .setFrameworkId(org.apache.mesos.v1.Protos.FrameworkID.newBuilder()
                                    .setValue("dummy-framework")
                                    .build())
                            .setHeartbeatIntervalSeconds(0)
                            .build())
                    .setType(Protos.Event.Type.SUBSCRIBED)
                    .build();
            driver.received(mesos, subscribedEvent);

            return null;
        }).doNothing().when(subscribeTimer).schedule(any(TimerTask.class), anyLong());

        driver.connected(mesos);

        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);

        /*
         mesos.send() should not be invoked by subscribe timer, after we get disconnect.
         */
        verify(mesos, times(2)).send(callCaptor.capture());
        final Protos.Call value = callCaptor.getValue();
        assertEquals(value.getType(), Protos.Call.Type.SUBSCRIBE);
    }

    @Test
    public void testReconnect() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);
        Timer heartbeatTimer = mock(Timer.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, heartbeatTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        doAnswer((InvocationOnMock invocation) -> {
            TimerTask callback = (TimerTask) invocation.getArguments()[0];
            callback.run();
            return null;
        }).when(heartbeatTimer).schedule(any(TimerTask.class), anyLong(), anyLong());

        // Set the heartbeat interval to 0. This should result in `reconnect()` being invoked immediately.
        driver.received(mesos, org.apache.mesos.v1.scheduler.Protos.Event.newBuilder()
                .setSubscribed(org.apache.mesos.v1.scheduler.Protos.Event.Subscribed.newBuilder()
                        .setFrameworkId(org.apache.mesos.v1.Protos.FrameworkID.newBuilder()
                                .setValue("dummy-framework")
                                .build())
                        .setHeartbeatIntervalSeconds(0)
                        .build())
                .setType(Protos.Event.Type.SUBSCRIBED)
                .build());

        verify(mesos, times(1)).reconnect();
    }

    private static class CustomMesosToSchedulerDriverAdapter extends MesosToSchedulerDriverAdapter {
        private final Mesos mesos;
        private final Timer timer;

        CustomMesosToSchedulerDriverAdapter(Scheduler scheduler, Mesos mesos) {
            super(scheduler, FRAMEWORK_INFO, "master");
            this.mesos = mesos;
            this.timer = new Timer();
        }

        CustomMesosToSchedulerDriverAdapter(Scheduler scheduler, Mesos mesos, Timer timer) {
            super(scheduler, FRAMEWORK_INFO, "master");
            this.mesos = mesos;
            this.timer = timer;
        }

        @Override
        protected Mesos startInternal() {
            return mesos;
        }

        @Override
        protected Timer createTimerInternal() {
            return timer;
        }
    }
}
