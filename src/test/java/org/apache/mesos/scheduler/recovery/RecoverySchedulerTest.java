package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.recovery.constrain.TestingLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.TestingFailureMonitor;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private DefaultRecoveryScheduler recoveryScheduler;
    private TaskFailureListener taskFailureListener;
    private RecoveryRequirementProvider recoveryRequirementProvider;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private SchedulerDriver schedulerDriver;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private AtomicReference<RecoveryStatus> recoveryStatusRef;

    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement) {
        return new DefaultRecoveryRequirement(
                offerRequirement,
                RecoveryRequirement.RecoveryType.NONE);
    }

    private List<Offer> getOffers(double cpus, double mem) {
        return OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem)));
    }

    @Before
    public void setupTest() {
        failureMonitor = spy(new TestingFailureMonitor());
        launchConstrainer = spy(new TestingLaunchConstrainer());
        recoveryStatusRef = new AtomicReference<>(new RecoveryStatus(Collections.emptyList(), Collections.emptyList()));
        taskFailureListener = mock(TaskFailureListener.class);
        offerAccepter = mock(OfferAccepter.class);
        recoveryRequirementProvider = mock(RecoveryRequirementProvider.class);
        stateStore = mock(StateStore.class);
        recoveryScheduler = spy(
                new DefaultRecoveryScheduler(
                        stateStore,
                        taskFailureListener,
                        recoveryRequirementProvider,
                        offerAccepter,
                        launchConstrainer,
                        failureMonitor,
                        recoveryStatusRef));
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
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                new OfferRequirement(infos, Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        launchConstrainer.setCanLaunch(false);

        List<Protos.OfferID> acceptedOffers = recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());
        assertEquals(0, acceptedOffers.size());

        // Verify launchConstrainer was used
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());
            //verify the UI
            assertEquals(Collections.singletonList(TestConstants.TASK_NAME), recoveryStatusRef.get().getStoppedNames());
            assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getFailedNames());
        }
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppededDoRelaunch() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = recoveryScheduler.resourceOffers(schedulerDriver, offers, Optional.empty());
        assertEquals(1, acceptedOffers.size());

        // Verify launchConstrainer was checked before launch
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify we ran launching code
        verify(offerAccepter, times(1)).accept(any(), any());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
    }

    @Test
    public void blockWithSameNameNoLaunch() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        launchConstrainer.setCanLaunch(true);

        Block block = mock(Block.class);
        when(block.getName()).thenReturn(TestConstants.TASK_NAME);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);

        List<Protos.OfferID> acceptedOffers =
                recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.of(block));
        assertEquals(0, acceptedOffers.size());

        // Verify the RecoveryStatus has empty pools.
        assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getFailed());
        assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getStopped());
    }

    @Test
    public void blockWithDifferentNameLaunches() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        launchConstrainer.setCanLaunch(true);

        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);

        Block block = mock(Block.class);
        when(block.getName()).thenReturn("different-name");

        List<Protos.OfferID> acceptedOffers =
                recoveryScheduler.resourceOffers(schedulerDriver, offers, Optional.of(block));
        assertEquals(1, acceptedOffers.size());
    }

    @Test
    public void stoppedTaskTransitionsToFailed() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(false);

        recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());

        // Verify we performed the failed task callback.
        verify(taskFailureListener, times(2)).taskFailed(TestConstants.TASK_ID);

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());
            // verify the transition to stopped
            assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getStopped());
            assertEquals(Collections.singletonList(TestConstants.TASK_NAME), recoveryStatusRef.get().getFailedNames());
        }
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        List<Offer> offers = getOffers(1.0, 1.0);
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));

        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());
        assertEquals(1, acceptedOffers.size());

        // Verify we launched the task
        ArgumentCaptor<List> operationCaptor = ArgumentCaptor.forClass(List.class);
        verify(offerAccepter, times(1)).accept(any(), operationCaptor.capture());
        assertEquals(3, operationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(2)).taskFailed(TestConstants.TASK_ID);

        // Verify the Task is reported as failed.
        assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(TestConstants.TASK_NAME), recoveryStatusRef.get().getFailedNames());
    }

    @Test
    public void failedTasksAreNotLaunchedWithInsufficientResources() throws Exception {
        double desiredCpu = 1.0;
        double desiredMem = 1.0;
        double insufficientCpu = desiredCpu / 2;
        double insufficientMem = desiredMem / 2;

        Resource cpus = ResourceTestUtils.getDesiredCpu(desiredCpu);
        Resource mem = ResourceTestUtils.getDesiredMem(desiredMem);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(infos, Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));

        List<Offer> insufficientOffers = getOffers(insufficientCpu, insufficientMem);

        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = recoveryScheduler.resourceOffers(schedulerDriver, insufficientOffers, Optional.empty());
        assertEquals(0, acceptedOffers.size());

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(2)).taskFailed(TestConstants.TASK_ID);

        // Verify we transitioned the task to failed
        assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(TestConstants.TASK_NAME), recoveryStatusRef.get().getFailedNames());

        // Verify we didn't launch the task
        verify(offerAccepter, times(1)).accept(any(), eq(Collections.EMPTY_LIST));
        verify(launchConstrainer, never()).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
    }

    @Test
    public void permanentlyFailedTasksAreRescheduled() throws Exception {
        // Prepare permanently failed task with some reserved resources
        Resource cpus = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        TaskInfo failedTaskInfo = FailureUtils.markFailed(taskInfo);
        List<TaskInfo> infos = Collections.singletonList(failedTaskInfo);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);

        List<Offer> offers = getOffers(1.0, 1.0);
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                new OfferRequirement(Collections.singletonList(ResourceUtils.clearResourceIds(taskInfo)),
                        Optional.empty(), Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(eq(infos)))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        List<Protos.OfferID> acceptedOffers = recoveryScheduler.resourceOffers(schedulerDriver, getOffers(1.0, 1.0), Optional.empty());
        assertEquals(1, acceptedOffers.size());

        // Verify we launched the task
        ArgumentCaptor<List> operationCaptor = ArgumentCaptor.forClass(List.class);
        verify(offerAccepter, times(1)).accept(any(), operationCaptor.capture());
        assertEquals(2, operationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the Task is reported as failed.
        assertEquals(Collections.EMPTY_LIST, recoveryStatusRef.get().getStopped());
        assertEquals(Collections.singletonList(TestConstants.TASK_NAME), recoveryStatusRef.get().getFailedNames());

        // Verify the appropriate task was not checked for failure with failure monitor.
        verify(failureMonitor, never()).hasFailed(any());
    }
}
