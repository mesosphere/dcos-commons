package org.apache.mesos.scheduler.recovery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.DefaultTaskKiller;
import org.apache.mesos.scheduler.plan.*;
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
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Our goal is verify the following pieces of functionality:
 * <ul>
 * <li>If it has failed tasks, it will not attempt to launch stopped tasks.</li>
 * <li> If a block is currently running with the same name as a terminated task, it will not appear as terminated.</li>
 * <li>If a task is failed, it can transition to stopped when the failure detector decides </li>
 * <li>If a task is stopped, it can be restarted (or maybe not, depending on offer)</li>
 * <li>Launches will only occur when the constrainer allows it</li>
 * <li>When a task is failed, it shows up as failed for multiple cycles if nothing changes</li>
 * <li>When a task is stopped, it shows up as stopped for multiple cycles if nothing changes</li>
 * <li>When a stopped task launches, it no longer shows up as stopped</li>
 * <li>When a failed task launches, it no longer shows up as failed</li>
 * </ul>
 */
public class SimpleRecoveryPlanManagerTest {
    private SimpleRecoveryPlanManager recoveryManager;
    private TaskFailureListener taskFailureListener;
    private RecoveryRequirementProvider recoveryRequirementProvider;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private SchedulerDriver schedulerDriver;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private PlanCoordinator planCoordinator;
    private PlanManager mockDeployManager;

    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement) {
        return getRecoveryRequirement(offerRequirement, RecoveryRequirement.RecoveryType.NONE);
    }

    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement,
                                                       RecoveryRequirement.RecoveryType recoveryType) {
        return new DefaultRecoveryRequirement(offerRequirement, recoveryType);
    }

    private List<Offer> getOffers(double cpus, double mem) {
        return OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem)));
    }

    @Captor
    private ArgumentCaptor<List<OfferRecommendation>> recommendationCaptor;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        failureMonitor = spy(new TestingFailureMonitor());
        launchConstrainer = spy(new TestingLaunchConstrainer());
        taskFailureListener = mock(TaskFailureListener.class);
        offerAccepter = mock(OfferAccepter.class);
        recoveryRequirementProvider = mock(RecoveryRequirementProvider.class);
        stateStore = mock(StateStore.class);
        recoveryManager = spy(
                new SimpleRecoveryPlanManager(
                        stateStore,
                        taskFailureListener,
                        recoveryRequirementProvider,
                        launchConstrainer,
                        failureMonitor));
        schedulerDriver = mock(SchedulerDriver.class);
        mockDeployManager = mock(PlanManager.class);
        final Plan mockDeployPlan = mock(Plan.class);
        when(mockDeployManager.getPlan()).thenReturn(mockDeployPlan);
        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.empty());
        when(mockDeployManager.isInterrupted()).thenReturn(false);
        final DefaultPlanScheduler planScheduler = new DefaultPlanScheduler(offerAccepter,
                new OfferEvaluator(stateStore),
                new DefaultTaskKiller(stateStore, taskFailureListener, schedulerDriver));
        planCoordinator = new DefaultPlanCoordinator(Arrays.asList(mockDeployManager, recoveryManager),
                planScheduler);
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
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos),
                RecoveryRequirement.RecoveryType.TRANSIENT);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        launchConstrainer.setCanLaunch(false);
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));
        assertEquals(0, acceptedOffers.size());

        // Verify launchConstrainer was used
        verify(launchConstrainer, times(1)).canLaunch(any());
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTask(taskInfo.getName()))
                .thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));
            //verify the UI
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getPhases());
            assertNotNull(recoveryManager.getPlan().getPhases().get(0).getBlocks());
            assertTrue(recoveryManager.getPlan().getPhases().get(0).getBlocks().size() == 1);
            assertEquals(TestConstants.TASK_NAME,
                    recoveryManager.getPlan().getPhases().get(0).getBlocks().get(0).getName());
            final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryBlock) recoveryManager.getPlan()
                    .getPhases().get(0).getBlocks().get(0)).getRecoveryRequirement().getRecoveryType();
            assertTrue(recoveryType == RecoveryRequirement.RecoveryType.TRANSIENT);
        }
        reset(mockDeployManager);
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppededDoRelaunch() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTask(taskInfo.getName()))
                .thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        // no dirty
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, offers);
        assertEquals(1, acceptedOffers.size());

        // Verify launchConstrainer was checked before launch
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify we ran launching code
        verify(offerAccepter, times(1)).accept(any(), any());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
        reset(mockDeployManager);
    }

    @Test
    public void blockWithSameNameNoLaunch() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        launchConstrainer.setCanLaunch(true);
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos));
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        Block block = mock(Block.class);
        when(block.getName()).thenReturn(TestConstants.TASK_NAME);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTask(taskInfo.getName())).thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));
        // 1 dirty
        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.of(block));
        Collection<Protos.OfferID> acceptedOffers =
                planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));
        assertEquals(0, acceptedOffers.size());

        // Verify the RecoveryStatus has empty pools.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getPhases());
        assertTrue(CollectionUtils.isNotEmpty(recoveryManager.getPlan().getPhases()));
        assertNotNull(recoveryManager.getPlan().getPhases().get(0));
        assertTrue(CollectionUtils.isNotEmpty(recoveryManager.getPlan().getPhases().get(0).getBlocks()));
        assertTrue(recoveryManager.getPlan().getPhases().get(0).getBlocks().stream().allMatch(b -> b.isPending()));
        reset(mockDeployManager);
    }

    @Test
    public void blockWithDifferentNameLaunches() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<Offer> offers = getOffers(1.0, 1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos));
        launchConstrainer.setCanLaunch(true);
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTask(taskInfo.getName())).thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));

        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);

        Block block = mock(Block.class);
        when(block.getName()).thenReturn("different-name");

        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.of(block));
        Collection<Protos.OfferID> acceptedOffers =
                planCoordinator.processOffers(schedulerDriver, offers);
        assertEquals(1, acceptedOffers.size());
        reset(mockDeployManager);
    }

    @Test
    public void stoppedTaskTransitionsToFailed() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(FailureUtils
                .markFailed(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem))));
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);

        when(stateStore.fetchTask(taskInfo.getName())).thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos),
                RecoveryRequirement.RecoveryType.PERMANENT);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        failureMonitor.setFailedList(taskInfo);
        launchConstrainer.setCanLaunch(false);
        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.empty());
        planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));

        // Verify we performed the failed task callback.
        verify(taskFailureListener, times(1)).taskFailed(TestConstants.TASK_ID);

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));

            // verify the transition to stopped
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getPhases());
            assertNotNull(recoveryManager.getPlan().getPhases().get(0).getBlocks());
            assertTrue(recoveryManager.getPlan().getPhases().get(0).getBlocks().size() == 1);
            assertEquals(TestConstants.TASK_NAME,
                    recoveryManager.getPlan().getPhases().get(0).getBlocks().get(0).getName());
            final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryBlock) recoveryManager.getPlan()
                    .getPhases().get(0).getBlocks().get(0)).getRecoveryRequirement().getRecoveryType();
            assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);
        }
        reset(mockDeployManager);
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.getTaskInfo(Arrays.asList(cpus, mem)));
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        List<Offer> offers = getOffers(1.0, 1.0);
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos),
                RecoveryRequirement.RecoveryType.PERMANENT);
        final TaskInfo taskInfo = infos.get(0);
        when(stateStore.fetchTask(taskInfo.getName())).thenReturn(Optional.of(taskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(status));
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(true);

        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));
        assertEquals(1, acceptedOffers.size());

        // Verify we launched the task
        verify(offerAccepter, times(1)).accept(any(), recommendationCaptor.capture());
        assertEquals(3, recommendationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(1)).taskFailed(TestConstants.TASK_ID);

        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getPhases());
        assertNotNull(recoveryManager.getPlan().getPhases().get(0).getBlocks());
        assertTrue(recoveryManager.getPlan().getPhases().get(0).getBlocks().size() == 1);
        assertEquals(TestConstants.TASK_NAME,
                recoveryManager.getPlan().getPhases().get(0).getBlocks().get(0).getName());
        final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryBlock) recoveryManager.getPlan()
                .getPhases().get(0).getBlocks().get(0)).getRecoveryRequirement().getRecoveryType();
        assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);
        reset(mockDeployManager);
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
        RecoveryRequirement recoveryRequirement = getRecoveryRequirement(new OfferRequirement(TestConstants.TASK_TYPE, infos),
                RecoveryRequirement.RecoveryType.PERMANENT);

        List<Offer> insufficientOffers = getOffers(insufficientCpu, insufficientMem);

        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        final TaskInfo failedTaskInfo = infos.get(0);
        failureMonitor.setFailedList(failedTaskInfo);
        when(stateStore.fetchTask(failedTaskInfo.getName())).thenReturn(Optional.of(failedTaskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(failedTaskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(failedTaskInfo.getName())).thenReturn(Optional.of(status));
        launchConstrainer.setCanLaunch(true);

        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.empty());
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, insufficientOffers);
        assertEquals(0, acceptedOffers.size());

        // Verify the appropriate task was checked for failure.
        verify(taskFailureListener, times(1)).taskFailed(TestConstants.TASK_ID);

        // Verify we transitioned the task to failed
        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getPhases());
        assertNotNull(recoveryManager.getPlan().getPhases().get(0).getBlocks());
        assertTrue(recoveryManager.getPlan().getPhases().get(0).getBlocks().size() == 1);
        assertEquals(TestConstants.TASK_NAME,
                recoveryManager.getPlan().getPhases().get(0).getBlocks().get(0).getName());
        final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryBlock) recoveryManager.getPlan()
                .getPhases().get(0).getBlocks().get(0)).getRecoveryRequirement().getRecoveryType();
        assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);

        // Verify we didn't launch the task
        verify(offerAccepter, times(0)).accept(any(), eq(Collections.EMPTY_LIST));
        verify(launchConstrainer, never()).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
        reset(mockDeployManager);
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
                new OfferRequirement(TestConstants.TASK_TYPE, Collections.singletonList(ResourceUtils.clearResourceIds(taskInfo))));

        failureMonitor.setFailedList(failedTaskInfo);
        when(stateStore.fetchTask(failedTaskInfo.getName())).thenReturn(Optional.of(failedTaskInfo));
        Protos.TaskStatus status = TaskTestUtils.generateStatus(failedTaskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        when(stateStore.fetchStatus(failedTaskInfo.getName())).thenReturn(Optional.of(status));
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(eq(infos)))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        when(mockDeployManager.getCurrentBlock(Arrays.asList())).thenReturn(Optional.empty());
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers(1.0, 1.0));
        assertEquals(1, acceptedOffers.size());

        // Verify we launched the task
        verify(offerAccepter, times(1)).accept(any(), recommendationCaptor.capture());
        assertEquals(2, recommendationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the appropriate task was not checked for failure with failure monitor.
        verify(failureMonitor, never()).hasFailed(any());
        reset(mockDeployManager);
    }

    @Test
    public void testGetTerminatedTasksNoDirtyAssets() {
        Resource cpus = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfoA = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        TaskInfo taskInfoB = TaskInfo.newBuilder(TaskTestUtils.getTaskInfo(Arrays.asList(cpus)))
                .setTaskId(TaskUtils.toTaskId(TestConstants.TASK_NAME + "-B"))
                .setName(TestConstants.TASK_NAME + "-B").build();
        List<TaskInfo> infos = Arrays.asList(taskInfoA, taskInfoB);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        final Collection<TaskInfo> terminatedTasks = recoveryManager.getTerminatedTasks(Arrays.asList());
        assertEquals(2, terminatedTasks.size());
    }

    @Test
    public void testGetTerminatedTasksWithDirtyTerminatedAsset() {
        Resource cpus = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfoA = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        final String taskNameB = TestConstants.TASK_NAME + "-B";
        TaskInfo taskInfoB = TaskInfo.newBuilder(TaskTestUtils.getTaskInfo(Arrays.asList(cpus)))
                .setTaskId(TaskUtils.toTaskId(taskNameB))
                .setName(taskNameB).build();
        List<TaskInfo> infos = Arrays.asList(taskInfoA, taskInfoB);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        Block block = mock(Block.class);
        when(block.getName()).thenReturn(TestConstants.TASK_NAME);
        final Collection<TaskInfo> terminatedTasks = recoveryManager.getTerminatedTasks(Arrays.asList(block));
        assertEquals(1, terminatedTasks.size());
        assertEquals(taskNameB, terminatedTasks.iterator().next().getName());
    }

    @Test
    public void testGetTerminatedTasksWithDirtyNonTerminatedAsset() {
        Resource cpus = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfoA = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        final String taskNameB = TestConstants.TASK_NAME + "-B";
        TaskInfo taskInfoB = TaskInfo.newBuilder(TaskTestUtils.getTaskInfo(Arrays.asList(cpus)))
                .setTaskId(TaskUtils.toTaskId(taskNameB))
                .setName(taskNameB).build();
        List<TaskInfo> infos = Arrays.asList(taskInfoA, taskInfoB);
        when(stateStore.fetchTasksNeedingRecovery()).thenReturn(infos);
        Block block = mock(Block.class);
        when(block.getName()).thenReturn(TestConstants.TASK_NAME + "-C");
        final Collection<TaskInfo> terminatedTasks = recoveryManager.getTerminatedTasks(Arrays.asList(block));
        assertEquals(2, terminatedTasks.size());
    }

    @Test
    public void testGetRecoveryCandidates() {
        Resource cpus = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfoA = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        final String taskNameB = TestConstants.TASK_NAME + "-B";
        TaskInfo taskInfoB = TaskInfo.newBuilder(TaskTestUtils.getTaskInfo(Arrays.asList(cpus)))
                .setTaskId(TaskUtils.toTaskId(taskNameB))
                .setName(taskNameB).build();
        List<TaskInfo> infos = Arrays.asList(taskInfoA, taskInfoB);
        final Map<String, TaskInfo> recoveryCandidates = recoveryManager.getRecoveryCandidates(infos);
        assertNotNull(recoveryCandidates);
        assertEquals(2, recoveryCandidates.size());
        assertTrue(recoveryCandidates.containsKey(TestConstants.TASK_NAME));
        assertTrue(recoveryCandidates.containsKey(taskNameB));
    }
}
