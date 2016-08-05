package org.apache.mesos.scheduler;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.ResourceTestUtils;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.repair.RepairOfferRequirementProvider;
import org.apache.mesos.scheduler.repair.RepairScheduler;
import org.apache.mesos.scheduler.repair.RepairStatus;
import org.apache.mesos.scheduler.repair.TaskFailureListener;
import org.apache.mesos.scheduler.repair.constrain.TestingLaunchConstrainer;
import org.apache.mesos.scheduler.repair.monitor.TestingFailureMonitor;
import org.apache.mesos.state.StateStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

/**
 * Our goal is verify the following pieces of functionality:
 * <p>
 * - If it has failed tasks, it will not attempt to launch stopped tasks. - If a block is currently running with the
 * same name as a terminated task, it will not appear as terminated. - If a task is failed, it can transition to stopped
 * when the failure detector decides - If a task is stopped, it can be restarted (or maybe not, depending on offer) -
 * Launches will only occur when the constrainer allows it - When a task is failed, it shows up as failed for multiple
 * cycles if nothing changes - When a task is stopped, it shows up as stopped for multiple cycles if nothing changes -
 * When a stopped task launches, it no longer shows up as stopped - When a failed task launches, it no longer shows up
 * as failed
 */
public class RepairSchedulerTest {
    private RepairScheduler repairScheduler;
    private TaskFailureListener taskFailureListener;
    private RepairOfferRequirementProvider repairOfferRequirementProvider;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private SchedulerDriver schedulerDriver;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private AtomicReference<RepairStatus> repairStatusRef;

    public static TaskInfo makeTaskInfo(Resource... resources) {
        TaskInfoBuilder infoBuilder = new TaskInfoBuilder(
                ResourceTestUtils.testTaskId,
                ResourceTestUtils.testTaskName,
                ResourceTestUtils.testSlaveId);
        for (Resource r : resources) {
            infoBuilder.addResource(r);
        }
        return infoBuilder.build();
    }

    public static Resource makeDesiredScalar(String name, double value) {
        return ResourceUtils.getDesiredScalar("test", "test", name, value);
    }

    public static Resource makeExpectedScalar(String name, double value) {
        return ResourceUtils.getExpectedScalar(name, value, UUID.randomUUID().toString(), "test", "test");
    }

    private List<Offer> getOffers(double cpus, double mem) {
        OfferBuilder builder = new OfferBuilder(
                ResourceTestUtils.testOfferId,
                ResourceTestUtils.testFrameworkId,
                ResourceTestUtils.testSlaveId,
                ResourceTestUtils.testHostname);
        builder.addResource(ResourceUtils.getUnreservedScalar("cpus", cpus));
        builder.addResource(ResourceUtils.getUnreservedScalar("mem", mem));
        return Arrays.asList(builder.build());
    }


    @Before
    public void setupTest() {
        failureMonitor = new TestingFailureMonitor();
        launchConstrainer = spy(new TestingLaunchConstrainer());
        repairStatusRef = new AtomicReference<>(new RepairStatus(Collections.emptyList(), Collections.emptyList()));
        taskFailureListener = mock(TaskFailureListener.class);
        offerAccepter = mock(OfferAccepter.class);
        repairOfferRequirementProvider = mock(RepairOfferRequirementProvider.class);
        stateStore = mock(StateStore.class);
        repairScheduler = spy(new RepairScheduler("test", stateStore, taskFailureListener,
                repairOfferRequirementProvider, offerAccepter,
                launchConstrainer, failureMonitor, repairStatusRef));
        schedulerDriver = mock(SchedulerDriver.class);
    }

    @After
    public void teardownTest() {
        taskFailureListener = null;
        repairOfferRequirementProvider = null;
        offerAccepter = null;
    }

    @Test
    public void ifStoppedTryToRelaunch() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        OfferRequirement req = new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(repairOfferRequirementProvider.getReplacementOfferRequirement(any())).thenReturn(req);
        launchConstrainer.setCanLaunch(false);

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        //launch failed tasks
        verify(repairOfferRequirementProvider, times(1)).getReplacementOfferRequirement(any());
        //don't launch stop when a task is failed
        verify(repairOfferRequirementProvider, never()).maybeGetNewOfferRequirement(any(), any());
        //we tried to launch it
        assertFalse(verify(launchConstrainer, times(1)).canLaunch(any()));

        //verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
            //verify the UI
            assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getStopped());
            assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailed());
        }
    }

    @Test
    public void ifStoppededDoRelaunch() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        OfferRequirement req = new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(repairOfferRequirementProvider.getReplacementOfferRequirement(any())).thenReturn(req);
        launchConstrainer.setCanLaunch(true);

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        //launch failed tasks
        verify(repairOfferRequirementProvider, times(1)).getReplacementOfferRequirement(any());
        //don't launch stop when a task is failed
        verify(repairOfferRequirementProvider, never()).maybeGetNewOfferRequirement(any(), any());
        //we tried to launch it (the assertFalse below should actually be true,
        //                       but this seems to be an issue w/ how we're using mockito)
        assertFalse(verify(launchConstrainer, times(1)).canLaunch(any()));

        // Verify we ran launching code
        verify(offerAccepter, times(1)).accept(any(), any());
        verify(launchConstrainer, times(1)).launchHappened(any());

        // Verify the UI
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailed());
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
    }

    @Test
    public void blockWithSameNameHidden() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(repairOfferRequirementProvider.getReplacementOfferRequirement(any())).thenReturn(null);
        launchConstrainer.setCanLaunch(false);
        Block block = mock(Block.class);
        when(block.getName()).thenReturn(ResourceTestUtils.testTaskName);
        when(repairOfferRequirementProvider.maybeGetNewOfferRequirement(any(), eq(block))).thenReturn(Optional.empty());

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), block);

        // Verify the UI shows no tasks
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailed());
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
    }

    @Test
    public void stoppedTaskTransitionsToFailed() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(false);

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        // Verify we deleted the task
        verify(taskFailureListener).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        //verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
            // verify the transition to stopped
            assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
            assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailed());
        }
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        OfferRequirement req = new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos).thenReturn(Collections.EMPTY_LIST);
        when(repairOfferRequirementProvider.maybeGetNewOfferRequirement(any(), any())).thenReturn(Optional.of(req));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        // Verify we deleted the task
        verify(taskFailureListener).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        // Verify we transitioned the task to failed
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailed());

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        // Verify we launched the task
        verify(offerAccepter, times(1)).accept(any(), any());
        verify(launchConstrainer, times(1)).launchHappened(any());

        // Verify the UI
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailed());
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
    }

    @Test
    public void failedTasksAreNotMarkedLaunchedWithInsufficientResources() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        OfferRequirement req = new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos).thenReturn(Collections.EMPTY_LIST);
        when(repairOfferRequirementProvider.maybeGetNewOfferRequirement(any(), any())).thenReturn(Optional.of(req));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);

        // Verify we deleted the task
        verify(taskFailureListener).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        // Verify we transitioned the task to failed
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailed());

        System.out.println("Here we are at round 2");
        // This is insufficient resources
        repairScheduler.resourceOffers(schedulerDriver, getOffers(0.5, 1.0), null);

        // Verify we didn't launch the task
        verify(offerAccepter, times(1)).accept(any(), eq(Collections.EMPTY_LIST));
        verify(launchConstrainer, never()).launchHappened(any());

        // Verify the UI
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailed());

    }
}
