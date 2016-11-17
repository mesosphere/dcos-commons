package org.apache.mesos.scheduler.recovery;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.collections.CollectionUtils;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.DefaultTaskKiller;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.recovery.constrain.TestingLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.TestingFailureMonitor;
import org.apache.mesos.specification.ServiceSpec;
import org.apache.mesos.specification.TestPodFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
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
 * <li> If a step is currently running with the same name as a terminated task, it will not appear as terminated.</li>
 * <li>If a task is failed, it can transition to stopped when the failure detector decides </li>
 * <li>If a task is stopped, it can be restarted (or maybe not, depending on offer)</li>
 * <li>Launches will only occur when the constrainer allows it</li>
 * <li>When a task is failed, it shows up as failed for multiple cycles if nothing changes</li>
 * <li>When a task is stopped, it shows up as stopped for multiple cycles if nothing changes</li>
 * <li>When a stopped task launches, it no longer shows up as stopped</li>
 * <li>When a failed task launches, it no longer shows up as failed</li>
 * </ul>
 */
@Ignore
public class DefaultRecoveryPlanManagerTest {

    private static final List<Resource> resources = Arrays.asList(
            ResourceTestUtils.getDesiredCpu(TestPodFactory.CPU),
            ResourceTestUtils.getDesiredMem(TestPodFactory.MEM));

    private static final TaskInfo TASK_INFO = TaskTestUtils.getTaskInfo(resources);
    private static final Collection<TaskInfo> TASK_INFOS = Collections.singletonList(TASK_INFO);

    private DefaultRecoveryPlanManager recoveryManager;
    private RecoveryRequirementProvider recoveryRequirementProvider;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private SchedulerDriver schedulerDriver;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private PlanCoordinator planCoordinator;
    private PlanManager mockDeployManager;
    private TaskFailureListener taskFailureListener;
    private EnvironmentVariables environmentVariables;

    private static TestingServer testingServer;


    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement) {
        return getRecoveryRequirement(offerRequirement, RecoveryRequirement.RecoveryType.NONE);
    }

    private RecoveryRequirement getRecoveryRequirement(OfferRequirement offerRequirement,
                                                       RecoveryRequirement.RecoveryType recoveryType) {
        return new DefaultRecoveryRequirement(offerRequirement, recoveryType, null);
    }

    private static List<Offer> getOffers() {
        return getOffers(TestPodFactory.CPU, TestPodFactory.MEM);
    }

    private static List<Offer> getOffers(double cpus, double mem) {
        return OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem)));
    }

    @Captor
    private ArgumentCaptor<List<OfferRecommendation>> recommendationCaptor;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        failureMonitor = spy(new TestingFailureMonitor());
        launchConstrainer = spy(new TestingLaunchConstrainer());
        offerAccepter = mock(OfferAccepter.class);
        recoveryRequirementProvider = mock(RecoveryRequirementProvider.class);
        stateStore = new CuratorStateStore(
                "test-framework-name",
                testingServer.getConnectString());
        configStore = DefaultScheduler.createConfigStore(
                mock(ServiceSpec.class),
                testingServer.getConnectString(),
                Collections.emptyList());
        taskFailureListener = mock(TaskFailureListener.class);
        recoveryManager = spy(new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                recoveryRequirementProvider,
                launchConstrainer,
                failureMonitor));
        schedulerDriver = mock(SchedulerDriver.class);
        mockDeployManager = mock(PlanManager.class);
        final Plan mockDeployPlan = mock(Plan.class);
        when(mockDeployManager.getPlan()).thenReturn(mockDeployPlan);
        final DefaultPlanScheduler planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(stateStore),
                new DefaultTaskKiller(stateStore, taskFailureListener, schedulerDriver));
        planCoordinator = new DefaultPlanCoordinator(Arrays.asList(mockDeployManager, recoveryManager),
                planScheduler);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
    }

    @Test
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppedTryConstrainedlaunch() throws Exception {
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS),
                RecoveryRequirement.RecoveryType.TRANSIENT);
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(TASK_INFO.getTaskId(), Protos.TaskState.TASK_FAILED);

        launchConstrainer.setCanLaunch(false);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(
                Arrays.asList(recoveryRequirement));
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        recoveryManager.update(status);
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers());

        assertEquals(0, acceptedOffers.size());
        // Verify launchConstrainer was used
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planCoordinator.processOffers(schedulerDriver, getOffers());
            //verify the UI
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getChildren());
            assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
            assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
            assertEquals(TestConstants.TASK_NAME,
                    recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
            final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryStep) recoveryManager.getPlan()
                    .getChildren().get(0).getChildren().get(0)).getRecoveryRequirement().getRecoveryType();
            assertTrue(recoveryType == RecoveryRequirement.RecoveryType.TRANSIENT);
        }
        reset(mockDeployManager);
    }

    @Test
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppedDoRelaunch() throws Exception {
        final List<Offer> offers = getOffers();
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS));
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(TASK_INFO.getTaskId(), Protos.TaskState.TASK_FAILED);

        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        launchConstrainer.setCanLaunch(true);

        recoveryManager.update(status);

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
    @SuppressWarnings("unchecked")
    public void stepWithSameNameNoLaunch() throws Exception {
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS));
        final Step step = mock(Step.class);
        Protos.TaskStatus status = TaskTestUtils.generateStatus(TASK_INFO.getTaskId(), Protos.TaskState.TASK_FAILED);

        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(step.getName()).thenReturn(TestConstants.TASK_NAME);
        // 1 dirty
        when(mockDeployManager.getCandidates(Arrays.asList())).thenReturn((Collection) Arrays.asList(step));

        recoveryManager.update(status);
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers());

        assertEquals(0, acceptedOffers.size());
        // Verify the RecoveryStatus has empty pools.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getChildren());
        assertTrue(CollectionUtils.isNotEmpty(recoveryManager.getPlan().getChildren()));
        assertNotNull(recoveryManager.getPlan().getChildren().get(0));
        assertTrue(CollectionUtils.isNotEmpty(recoveryManager.getPlan().getChildren().get(0).getChildren()));
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().stream().allMatch(b -> b.isPending()));
        reset(mockDeployManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void stepWithDifferentNameLaunches() throws Exception {
        final List<Offer> offers = getOffers();
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(TASK_INFO.getTaskId(), Protos.TaskState.TASK_FAILED);
        final RecoveryRequirement recoveryRequirement =
                getRecoveryRequirement(OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS));
        final Step step = mock(Step.class);

        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        when(step.getName()).thenReturn("different-name");
        when(mockDeployManager.getCandidates(Arrays.asList())).thenReturn((Collection) Arrays.asList(step));

        recoveryManager.update(status);
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, offers);

        assertEquals(1, acceptedOffers.size());
        reset(mockDeployManager);
    }

    @Test
    public void stoppedTaskTransitionsToFailed() throws Exception {
        final List<TaskInfo> infos = Collections.singletonList(FailureUtils.markFailed(TASK_INFO));
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(TASK_INFO.getTaskId(), Protos.TaskState.TASK_FAILED);
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, infos),
                RecoveryRequirement.RecoveryType.PERMANENT);

        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(false);
        stateStore.storeTasks(infos);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any())).thenReturn(Arrays.asList(recoveryRequirement));
        when(mockDeployManager.getCandidates(Arrays.asList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        planCoordinator.processOffers(schedulerDriver, getOffers());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planCoordinator.processOffers(schedulerDriver, getOffers());

            // verify the transition to stopped
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getChildren());
            assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
            assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
            assertEquals(TestConstants.TASK_NAME,
                    recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
            final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryStep) recoveryManager.getPlan()
                    .getChildren().get(0).getChildren().get(0)).getRecoveryRequirement().getRecoveryType();
            assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);
        }
        reset(mockDeployManager);
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        final List<Offer> offers = getOffers();
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS),
                RecoveryRequirement.RecoveryType.PERMANENT);
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(TASK_INFO);
        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));

        recoveryManager.update(status);
        final Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(
                schedulerDriver,
                getOffers());

        // Verify we launched the task
        assertEquals(1, acceptedOffers.size());
        verify(offerAccepter, times(1)).accept(any(), recommendationCaptor.capture());
        assertEquals(3, recommendationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getChildren());
        assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
        assertEquals(TestConstants.TASK_NAME,
                recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
        final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryStep) recoveryManager.getPlan()
                .getChildren().get(0).getChildren().get(0)).getRecoveryRequirement().getRecoveryType();
        assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);
        reset(mockDeployManager);
    }

    @Test
    public void failedTasksAreNotLaunchedWithInsufficientResources() throws Exception {
        final double insufficientCpu = TestPodFactory.CPU / 2.;
        final double insufficientMem = TestPodFactory.MEM / 2.;

        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS),
                RecoveryRequirement.RecoveryType.PERMANENT);
        final List<Offer> insufficientOffers = getOffers(insufficientCpu, insufficientMem);
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(TASK_INFO);
        launchConstrainer.setCanLaunch(true);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(status);
        when(mockDeployManager.getCandidates(Arrays.asList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        final Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, insufficientOffers);

        assertEquals(0, acceptedOffers.size());
        // Verify we transitioned the task to failed
        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getChildren());
        assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
        assertEquals(TestConstants.TASK_NAME,
                recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
        final RecoveryRequirement.RecoveryType recoveryType = ((DefaultRecoveryStep) recoveryManager.getPlan()
                .getChildren().get(0).getChildren().get(0)).getRecoveryRequirement().getRecoveryType();
        assertTrue(recoveryType == RecoveryRequirement.RecoveryType.PERMANENT);

        // Verify we didn't launch the task
        verify(offerAccepter, times(0)).accept(any(), eq(new ArrayList<>()));
        verify(launchConstrainer, never()).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));
        reset(mockDeployManager);
    }

    @Test
    public void permanentlyFailedTasksAreRescheduled() throws Exception {
        // Prepare permanently failed task with some reserved resources
        final Resource cpus = ResourceTestUtils.getExpectedCpu(TestPodFactory.CPU);
        final TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpus));
        final TaskInfo failedTaskInfo = FailureUtils.markFailed(taskInfo);
        final List<TaskInfo> infos = Collections.singletonList(failedTaskInfo);
        final List<Offer> offers = getOffers();
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(
                        TestConstants.TASK_TYPE,
                        0,
                        Collections.singletonList(ResourceUtils.clearResourceIds(taskInfo))));
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                failedTaskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(failedTaskInfo);
        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(infos);
        stateStore.storeStatus(status);
        when(recoveryRequirementProvider.getPermanentRecoveryRequirements(eq(infos)))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));
        when(mockDeployManager.getCandidates(Arrays.asList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        Collection<Protos.OfferID> acceptedOffers = planCoordinator.processOffers(schedulerDriver, getOffers());

        assertEquals(1, acceptedOffers.size());

        // Verify we launched the task
        verify(offerAccepter, times(1)).accept(any(), recommendationCaptor.capture());
        assertEquals(2, recommendationCaptor.getValue().size());
        verify(launchConstrainer, times(1)).launchHappened(any(), eq(recoveryRequirement.getRecoveryType()));

        // Verify the appropriate task was not checked for failure with failure monitor.
        verify(failureMonitor, never()).hasFailed(any());
        reset(mockDeployManager);
    }

    /**
     * Tests that if we receive duplicate TASK_FAILED messages for the same task, only one step is created in the
     * recovery plan.
     */
    @Test
    public void testUpdateTaskFailsTwice() throws Exception {
        final List<Offer> offers = getOffers();
        final Protos.TaskStatus runningStatus = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_RUNNING);
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_FAILED);
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS));

        launchConstrainer.setCanLaunch(true);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));

        // TASK_RUNNING
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(runningStatus);
        recoveryManager.update(runningStatus);
        assertEquals(0, recoveryManager.getPlan().getChildren().size());

        // TASK_FAILED
        stateStore.storeStatus(failedStatus);
        recoveryManager.update(failedStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_FAILED
        stateStore.storeStatus(failedStatus);
        recoveryManager.update(failedStatus);
        assertEquals(1, recoveryManager.getPlan().getChildren().get(0).getChildren().size());
    }

    @Test
    public void testMultipleFailuresSingleTask() throws Exception {
        final List<Offer> offers = getOffers();
        final Protos.TaskStatus runningStatus = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_RUNNING);
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                TASK_INFO.getTaskId(),
                Protos.TaskState.TASK_FAILED);
        final RecoveryRequirement recoveryRequirement = getRecoveryRequirement(
                OfferRequirement.create(TestConstants.TASK_TYPE, TestConstants.TASK_INDEX, TASK_INFOS));

        launchConstrainer.setCanLaunch(true);
        when(recoveryRequirementProvider.getTransientRecoveryRequirements(any()))
                .thenReturn(Arrays.asList(recoveryRequirement));
        when(offerAccepter.accept(any(), any())).thenReturn(Arrays.asList(offers.get(0).getId()));

        // TASK_RUNNING
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(runningStatus);
        recoveryManager.update(runningStatus);
        assertEquals(0, recoveryManager.getPlan().getChildren().size());

        // TASK_FAILED
        stateStore.storeStatus(failedStatus);
        recoveryManager.update(failedStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_RUNNING
        stateStore.storeTasks(TASK_INFOS);
        stateStore.storeStatus(runningStatus);
        recoveryManager.update(runningStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_FAILED
        stateStore.storeStatus(failedStatus);
        recoveryManager.update(failedStatus);
        assertEquals(1, recoveryManager.getPlan().getChildren().get(0).getChildren().size());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());
    }

}
