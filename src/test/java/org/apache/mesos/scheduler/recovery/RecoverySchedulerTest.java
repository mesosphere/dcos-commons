package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
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
import org.apache.mesos.scheduler.recovery.constrain.TestingLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.TestingFailureMonitor;
import org.apache.mesos.state.StateStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
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
public class RecoverySchedulerTest {
    private DefaultRecoveryScheduler repairScheduler;
    private TaskFailureListener taskFailureListener;
    private RecoveryRequirementProvider recoveryRequirementProvider;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private SchedulerDriver schedulerDriver;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private AtomicReference<RecoveryStatus> repairStatusRef;

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

    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement) {
        return new DefaultRecoveryRequirement(
                offerRequirement,
                RecoveryRequirement.RecoveryType.NONE);
    }

    @Before
    public void setupTest() {
        failureMonitor = new TestingFailureMonitor();
        launchConstrainer = spy(new TestingLaunchConstrainer());
        repairStatusRef = new AtomicReference<>(new RecoveryStatus(Collections.emptyList(), Collections.emptyList()));
        taskFailureListener = mock(TaskFailureListener.class);
        offerAccepter = mock(OfferAccepter.class);
        recoveryRequirementProvider = mock(RecoveryRequirementProvider.class);
        stateStore = mock(StateStore.class);
        repairScheduler = spy(
                new DefaultRecoveryScheduler(
                        stateStore,
                        taskFailureListener,
                        recoveryRequirementProvider,
                        offerAccepter,
                        launchConstrainer,
                        failureMonitor,
                        repairStatusRef));
        schedulerDriver = mock(SchedulerDriver.class);
    }

    @After
    public void teardownTest() {
        taskFailureListener = null;
        recoveryRequirementProvider = null;
        offerAccepter = null;
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppedTryConstrainedlaunch() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(recoveryRequirementProvider.getTransientRecoveryOfferRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        launchConstrainer.setCanLaunch(false);

        List<Protos.OfferID> acceptedOffers = repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
        assertEquals(0, acceptedOffers.size());

        // Verify launchConstrainer was used
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
            //verify the UI
            assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getStoppedNames());
            assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailedNames());
        }
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppededDoRelaunch() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(recoveryRequirementProvider.getTransientRecoveryOfferRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = repairScheduler.resourceOffers(schedulerDriver, offers, null);
        assertEquals(1, acceptedOffers.size());

        // Verify launchConstrainer was checked before launch
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify we ran launching code
        verify(offerAccepter, times(1)).accept(any(), any());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
    }

    @Test
    public void blockWithSameNameNoLaunch() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        launchConstrainer.setCanLaunch(true);

        Block block = mock(Block.class);
        when(block.getName()).thenReturn(ResourceTestUtils.testTaskName);
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);

        List<Protos.OfferID> acceptedOffers = repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), block);
        assertEquals(0, acceptedOffers.size());

        // Verify the RecoveryStatus has empty pools.
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getFailed());
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
    }

    @Test
    public void blockWithDifferentNameLaunches() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        launchConstrainer.setCanLaunch(true);

        when(recoveryRequirementProvider.getTransientRecoveryOfferRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);

        Block block = mock(Block.class);
        when(block.getName()).thenReturn("different-name");

        List<Protos.OfferID> acceptedOffers = repairScheduler.resourceOffers(schedulerDriver, offers, block);
        assertEquals(1, acceptedOffers.size());
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

        // Verify we performed the failed task callback.
        verify(taskFailureListener, times(2)).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
            // verify the transition to stopped
            assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
            assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailedNames());
        }
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        Resource cpus = makeDesiredScalar("cpus", 1.0);
        Resource mem = makeDesiredScalar("mem", 1.0);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        List<Offer> offers = getOffers(1.0, 1.0);
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST));

        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(recoveryRequirementProvider.getPermanentRecoveryOfferRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> accepteOffers = repairScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), null);
        assertEquals(1, accepteOffers.size());

        // Verify we launched the task
        ArgumentCaptor<List> operationCaptor = ArgumentCaptor.forClass(List.class);
        verify(offerAccepter, times(1)).accept(any(), operationCaptor.capture());
        assertEquals(3, operationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(2)).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        // Verify the Task is reported as failed.
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailedNames());
    }

    @Test
    public void failedTasksAreNotLaunchedWithInsufficientResources() throws Exception {
        double desiredCpu = 1.0;
        double desiredMem = 1.0;
        double insufficientCpu = desiredCpu / 2;
        double insufficientMem = desiredMem / 2;

        Resource cpus = makeDesiredScalar("cpus", desiredCpu);
        Resource mem = makeDesiredScalar("mem", desiredMem);
        List<TaskInfo> infos = Collections.singletonList(makeTaskInfo(cpus, mem));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST));

        List<Offer> insufficientOffers = getOffers(insufficientCpu, insufficientMem);

        when(stateStore.fetchTerminatedTasks()).thenReturn(infos);
        when(recoveryRequirementProvider.getPermanentRecoveryOfferRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = repairScheduler.resourceOffers(schedulerDriver, insufficientOffers, null);
        assertEquals(0, acceptedOffers.size());

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(2)).taskFailed(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId).build());

        // Verify we transitioned the task to failed
        assertEquals(Collections.EMPTY_LIST, repairStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(ResourceTestUtils.testTaskName), repairStatusRef.get().getFailedNames());

        // Verify we didn't launch the task
        verify(offerAccepter, times(1)).accept(any(), eq(Collections.EMPTY_LIST));
        verify(launchConstrainer, never()).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
    }
}
