package org.apache.mesos.scheduler;

import com.google.protobuf.ByteString;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class MesosToSchedulerDriverAdapterTest {
    private static final org.apache.mesos.Protos.FrameworkID FRAMEWORK_ID = org.apache.mesos.Protos.FrameworkID
            .newBuilder()
            .setValue("framework-id")
            .build();

    private static final FrameworkInfo FRAMEWORK_INFO = FrameworkInfo
            .newBuilder()
            .setUser("Foo")
            .setName("Bar")
            .setId(FRAMEWORK_ID)
            .build();

    final org.apache.mesos.v1.Protos.AgentID agentId = org.apache.mesos.v1.Protos.AgentID.newBuilder()
            .setValue("agent-id").build();
    final org.apache.mesos.Protos.SlaveID slaveId = org.apache.mesos.Protos.SlaveID.newBuilder()
            .setValue("slave-id").build();
    final org.apache.mesos.v1.Protos.TaskID taskId = org.apache.mesos.v1.Protos.TaskID.newBuilder()
            .setValue("task-id").build();
    final org.apache.mesos.Protos.TaskID taskIdV0 = org.apache.mesos.Protos.TaskID.newBuilder()
            .setValue("task-id").build();
    final org.apache.mesos.v1.Protos.ExecutorID executorIdV1 = org.apache.mesos.v1.Protos.ExecutorID.newBuilder()
            .setValue("executorIdV1-id").build();
    final org.apache.mesos.Protos.ExecutorID executorIdV0 = org.apache.mesos.Protos.ExecutorID.newBuilder()
            .setValue("executorIdV0-id").build();
    final org.apache.mesos.Protos.OfferID offerId = org.apache.mesos.Protos.OfferID.newBuilder().setValue("offer-id")
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
    public void testOffers() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event offerEvent = Protos.Event.newBuilder().setType(Protos.Event.Type.OFFERS).buildPartial();
        driver.received(mesos, offerEvent);
        verify(scheduler, times(1)).resourceOffers(any(), any());
    }

    @Test
    public void testInverseOffers() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.INVERSE_OFFERS).buildPartial();
        driver.received(mesos, event);
        verifyZeroInteractions(scheduler);
    }

    @Test
    public void testRescind() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.RESCIND).buildPartial();
        driver.received(mesos, event);
        verify(scheduler, times(1)).offerRescinded(any(), any());
    }

    @Test
    public void testRescindInverseOffer() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.RESCIND_INVERSE_OFFER).buildPartial();
        driver.received(mesos, event);
        verifyZeroInteractions(scheduler);
    }

    @Test
    public void testMessage() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.MESSAGE).buildPartial();
        driver.received(mesos, event);
        verify(scheduler, times(1)).frameworkMessage(any(), any(), any(), any());
    }

    @Test
    public void testUpdate() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.UPDATE).buildPartial();
        driver.received(mesos, event);
        verify(scheduler, times(1)).statusUpdate(any(), any());
    }

    @Test
    public void testUpdateHasUUIDSendAck() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());



        Protos.Event event = Protos.Event.newBuilder()
                .setType(Protos.Event.Type.UPDATE)
                .setUpdate(Protos.Event.Update.newBuilder()
                        .setStatus(org.apache.mesos.v1.Protos.TaskStatus.newBuilder()
                            .setUuid(ByteString.copyFromUtf8(UUID.randomUUID().toString()))
                                .setAgentId(agentId)
                                .setTaskId(taskId)
                                .buildPartial())
                        .buildPartial())
                .buildPartial();
        driver.received(mesos, event);
        verify(scheduler, times(1)).statusUpdate(any(), any());
        verify(mesos, times(1)).send(any());
    }

    @Test
    public void testHeartbeat() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.HEARTBEAT).buildPartial();
        final Instant lastLastHeartbeat = driver.getLastHeartbeat();
        driver.received(mesos, event);
        final Instant lastHeartbeat = driver.getLastHeartbeat();
        assertNotEquals(lastHeartbeat, lastLastHeartbeat);
    }

    @Test
    public void testError() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.ERROR).buildPartial();
        driver.received(mesos, event);
        verify(scheduler, times(1)).error(any(), any());
    }

    @Test
    public void testFailureHasSlaveAndExec() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        final org.apache.mesos.v1.scheduler.Protos.Event.Failure.Builder failure =
                org.apache.mesos.v1.scheduler.Protos.Event.Failure.newBuilder()
                .setAgentId(agentId)
                .setExecutorId(executorIdV1)
                .setStatus(0);

        Protos.Event event = Protos.Event.newBuilder()
                .setType(Protos.Event.Type.FAILURE)
                .setFailure(failure)
                .buildPartial();

        driver.received(mesos, event);
        verify(scheduler, times(1)).executorLost(any(), any(), any(), anyInt());
    }

    @Test
    public void testFailureNoExecId() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        final org.apache.mesos.v1.scheduler.Protos.Event.Failure.Builder failure = org.apache.mesos.v1.scheduler.Protos.Event.Failure.newBuilder()
                .setAgentId(agentId)
                .setStatus(0);

        Protos.Event event = Protos.Event.newBuilder()
                .setType(Protos.Event.Type.FAILURE)
                .setFailure(failure)
                .buildPartial();

        driver.received(mesos, event);
        verify(scheduler, times(1)).slaveLost(any(), any());
    }

    @Test
    public void testUnknown() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        Protos.Event event = Protos.Event.newBuilder().setType(Protos.Event.Type.UNKNOWN).buildPartial();
        driver.received(mesos, event);
        verifyZeroInteractions(scheduler);
    }

    @Test
    public void testDisconnected() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        driver.connected(mesos);
        driver.disconnected(mesos);

        verify(scheduler, times(1)).disconnected(driver);
    }

    @Test
    public void testSubscribe() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);
        ScheduledExecutorService subscribeTimer = mock(ScheduledExecutorService.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        final AtomicBoolean once = new AtomicBoolean();
        when(subscribeTimer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer((InvocationOnMock invocation) -> {
            Runnable callback = (Runnable) invocation.getArguments()[0];
            if (!once.get()) {
                once.set(true);
                callback.run();
            }
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
        });

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
        ScheduledExecutorService subscribeTimer = mock(ScheduledExecutorService.class);
        when(subscribeTimer.shutdownNow()).thenReturn(Collections.EMPTY_LIST);
        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        final AtomicBoolean once = new AtomicBoolean();
        when(subscribeTimer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .then((InvocationOnMock invocation) -> {
                    Runnable callback = (Runnable) invocation.getArguments()[0];
                    if (!once.get()) {
                        once.set(true);
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
                    }
                    return null;
                });

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
        ScheduledExecutorService subscribeTimer = mock(ScheduledExecutorService.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, subscribeTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        final AtomicInteger counter = new AtomicInteger(2);
        when(subscribeTimer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .then((InvocationOnMock invocation) -> {
            Runnable callback = (Runnable) invocation.getArguments()[0];
            if (counter.get() == 2) {
                counter.decrementAndGet();
                callback.run();
                return null;
            } else if (counter.get() == 1) {
                counter.decrementAndGet();
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
            } else {
                return null;
            }
        });

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
        ScheduledExecutorService heartbeatTimer = mock(ScheduledExecutorService.class);

        CustomMesosToSchedulerDriverAdapter driver =
                new CustomMesosToSchedulerDriverAdapter(scheduler, mesos, heartbeatTimer);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());

        doAnswer((InvocationOnMock invocation) -> {
            Runnable callback = (Runnable) invocation.getArguments()[0];
            callback.run();
            return null;
        }).when(heartbeatTimer)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

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

    @Test
    public void testStop() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_STOPPED, driver.stop());
    }

    @Test
    public void testStopNotYetStarted() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED, driver.stop());
    }

    @Test
    public void testStopTrue() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        assertEquals(org.apache.mesos.Protos.Status.DRIVER_STOPPED, driver.stop(true));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testJoin() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        driver.join();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRun() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);
        driver.run();
    }

    @Test
    public void testRequestResources() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.requestResources(Collections.EMPTY_LIST);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.REQUEST, callCaptor.getValue().getType());
    }

    @Test
    public void testLaunchTasksWithoutFilters() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.launchTasks(
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACCEPT, callCaptor.getValue().getType());
    }

    @Test
    public void testLaunchTasksWithFilters() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.launchTasks(
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST,
                org.apache.mesos.Protos.Filters.getDefaultInstance());
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACCEPT, callCaptor.getValue().getType());
    }

    @Test
    public void testLaunchTasksSingleOfferWithFilters() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.launchTasks(offerId, Collections.EMPTY_LIST, org.apache.mesos.Protos.Filters.getDefaultInstance());
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACCEPT, callCaptor.getValue().getType());
    }

    @Test
    public void testLaunchTasksSingleOfferWithoutFilters() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.launchTasks(offerId, Collections.EMPTY_LIST);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACCEPT, callCaptor.getValue().getType());
    }

    @Test
    public void testKillTask() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.killTask(taskIdV0);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.KILL, callCaptor.getValue().getType());
    }

    @Test
    public void testAcceptOffers() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.acceptOffers(Collections.EMPTY_LIST, Collections.EMPTY_LIST
                , org.apache.mesos.Protos.Filters.getDefaultInstance());
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACCEPT, callCaptor.getValue().getType());
    }

    @Test
    public void testDeclineOfferWithFilter() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.declineOffer(offerId, org.apache.mesos.Protos.Filters.getDefaultInstance());
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.DECLINE, callCaptor.getValue().getType());
    }

    @Test
    public void testDeclineOffer() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.declineOffer(offerId);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.DECLINE, callCaptor.getValue().getType());
    }

    @Test
    public void testReviveOffers() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.reviveOffers();
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.REVIVE, callCaptor.getValue().getType());
    }

    @Test
    public void testSupressOffers() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.suppressOffers();
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.SUPPRESS, callCaptor.getValue().getType());
    }

    @Test
    public void testAck() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        final org.apache.mesos.Protos.TaskStatus status = org.apache.mesos.Protos.TaskStatus.newBuilder()
                .setSlaveId(slaveId)
                .setTaskId(taskIdV0)
                .setState(org.apache.mesos.Protos.TaskState.TASK_ERROR)
                .build();
        driver.acknowledgeStatusUpdate(status);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.ACKNOWLEDGE, callCaptor.getValue().getType());
    }

    @Test
    public void testSendFrameworkMessage() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        driver.sendFrameworkMessage(executorIdV0, slaveId, new byte[1]);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.MESSAGE, callCaptor.getValue().getType());
    }

    @Test
    public void testReconcile() {
        Scheduler scheduler = mock(Scheduler.class);
        Mesos mesos = mock(Mesos.class);

        CustomMesosToSchedulerDriverAdapter driver = new CustomMesosToSchedulerDriverAdapter(scheduler, mesos);

        assertEquals(org.apache.mesos.Protos.Status.DRIVER_RUNNING, driver.start());
        final ArrayList<org.apache.mesos.Protos.TaskStatus> taskStatuses = new ArrayList<>();
        taskStatuses.add(org.apache.mesos.Protos.TaskStatus.newBuilder()
                .setSlaveId(slaveId)
                .setTaskId(taskIdV0)
                .setState(org.apache.mesos.Protos.TaskState.TASK_ERROR)
                .build());
        driver.reconcileTasks(taskStatuses);
        final ArgumentCaptor<Protos.Call> callCaptor = ArgumentCaptor.forClass(Protos.Call.class);
        verify(mesos, times(1)).send(callCaptor.capture());
        assertEquals(Protos.Call.Type.RECONCILE, callCaptor.getValue().getType());
    }

    private static class CustomMesosToSchedulerDriverAdapter extends MesosToSchedulerDriverAdapter {
        private final Mesos mesos;
        private final ScheduledExecutorService timer;

        CustomMesosToSchedulerDriverAdapter(Scheduler scheduler, Mesos mesos) {
            super(scheduler, FRAMEWORK_INFO, "master");
            this.mesos = mesos;
            this.timer = Executors.newSingleThreadScheduledExecutor();
        }

        CustomMesosToSchedulerDriverAdapter(Scheduler scheduler, Mesos mesos, ScheduledExecutorService timer) {
            super(scheduler, FRAMEWORK_INFO, "master");
            this.mesos = mesos;
            this.timer = timer;
        }

        @Override
        protected Mesos startInternal() {
            return mesos;
        }

        @Override
        protected ScheduledExecutorService createTimerInternal() {
            return timer;
        }
    }
}
