package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.constrain.TestingLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.TestingFailureMonitor;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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
public class DefaultRecoveryPlanManagerTest extends DefaultCapabilitiesTestSuite {
    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private static final List<Resource> resources = Arrays.asList(
            ResourceTestUtils.getUnreservedCpus(TestPodFactory.CPU),
            ResourceTestUtils.getUnreservedMem(TestPodFactory.MEM));

    private TaskInfo taskInfo = TaskTestUtils.getTaskInfo(resources);
    private Collection<TaskInfo> taskInfos = Collections.singletonList(taskInfo);

    private DefaultRecoveryPlanManager recoveryManager;
    private FrameworkStore frameworkStore;
    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private TestingFailureMonitor failureMonitor;
    private TestingLaunchConstrainer launchConstrainer;
    private PlanCoordinator planCoordinator;
    private PlanScheduler planScheduler;
    private PlanManager mockDeployManager;
    private ServiceSpec serviceSpec;

    private static List<Offer> getOffers() {
        return getOffers(TestPodFactory.CPU, TestPodFactory.MEM);
    }

    private static List<Offer> getOffers(double cpus, double mem) {
        return OfferTestUtils.getCompleteOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpus(cpus),
                        ResourceTestUtils.getUnreservedMem(mem)));
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        failureMonitor = spy(new TestingFailureMonitor());
        launchConstrainer = spy(new TestingLaunchConstrainer());
        Persister persister = new MemPersister();
        frameworkStore = new FrameworkStore(persister);
        stateStore = new StateStore(persister);

        File recoverySpecFile = new File(getClass().getClassLoader().getResource("recovery-plan-manager-test.yml").getPath());
        serviceSpec = DefaultServiceSpec.newGenerator(recoverySpecFile, SCHEDULER_CONFIG).build();

        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);
        UUID configTarget = configStore.store(serviceSpec);
        configStore.setTargetConfig(configTarget);
        taskInfo = TaskInfo.newBuilder(taskInfo)
                .setLabels(new TaskLabelWriter(taskInfo)
                        .setTargetConfiguration(configTarget)
                        .setIndex(0)
                        .toProto())
                .setName("test-task-type-0-test-task-name")
                .setTaskId(CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, "test-task-type-0-test-task-name"))
                .build();

        taskInfos = Collections.singletonList(taskInfo);
        recoveryManager = spy(new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                new HashSet<>(Arrays.asList(taskInfo.getName())),
                launchConstrainer,
                failureMonitor,
                Optional.empty()));
        mockDeployManager = mock(PlanManager.class);
        final Plan mockDeployPlan = mock(Plan.class);
        when(mockDeployManager.getPlan()).thenReturn(mockDeployPlan);
        planScheduler = new PlanScheduler(
                new OfferEvaluator(
                        frameworkStore,
                        stateStore,
                        Optional.empty(),
                        serviceSpec.getName(),
                        configTarget,
                        PodTestUtils.getTemplateUrlFactory(),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        Optional.empty()),
                stateStore,
                Optional.empty());
        planCoordinator =
                new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(mockDeployManager, recoveryManager));
    }

    @Test
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppedTryConstrainedlaunch() throws Exception {
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        launchConstrainer.setCanLaunch(false);
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), status);
        recoveryManager.update(status);
        Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());

        assertEquals(0, recommendations.size());
        // Verify launchConstrainer was used
        verify(launchConstrainer, times(1)).canLaunch(any());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());
            //verify the UI
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getChildren());
            assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
            assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
            assertEquals("test-task-type-0:[test-task-name]",
                    recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
        }

        reset(mockDeployManager);
    }

    @Test
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void ifStoppedDoRelaunch() throws Exception {
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), status);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        launchConstrainer.setCanLaunch(true);

        recoveryManager.update(status);

        // no dirty
        Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());
        assertEquals(1, distinctOffers(recommendations).size());

        // Verify launchConstrainer was checked before launch
        verify(launchConstrainer, times(1)).canLaunch(any());

        reset(mockDeployManager);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void stepWithDifferentNameLaunches() throws Exception {
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);
        final Step step = mock(Step.class);

        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), status);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        when(step.getName()).thenReturn("different-name");
        when(mockDeployManager.getCandidates(Collections.emptyList())).thenReturn((Collection) Arrays.asList(step));

        recoveryManager.update(status);
        Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());

        assertEquals(1, distinctOffers(recommendations).size());
        reset(mockDeployManager);
    }

    @Test
    public void stoppedTaskTransitionsToFailed() throws Exception {
        final List<TaskInfo> infos = Collections.singletonList(TaskTestUtils.withFailedFlag(taskInfo));
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(infos.get(0));
        launchConstrainer.setCanLaunch(false);
        stateStore.storeTasks(infos);
        stateStore.storeStatus(taskInfo.getName(), status);
        when(mockDeployManager.getCandidates(Collections.emptyList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        planScheduler.resourceOffers(
                getOffers(),
                planCoordinator.getCandidates());

        // Verify that the UI remains stable
        for (int i = 0; i < 10; i++) {
            planScheduler.resourceOffers(
                    getOffers(),
                    planCoordinator.getCandidates());

            // verify the transition to stopped
            assertNotNull(recoveryManager.getPlan());
            assertNotNull(recoveryManager.getPlan().getChildren());
            assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
            assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
            assertEquals("test-task-type-0:[test-task-name]",
                    recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());
        }
        reset(mockDeployManager);
    }

    @Test
    public void failedTaskCanBeRestarted() throws Exception {
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(taskInfo);
        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), status);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);

        recoveryManager.update(status);
        final Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());

        // Verify we launched the task
        assertEquals(1, distinctOffers(recommendations).size());

        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getChildren());
        assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
        assertEquals("test-task-type-0:[test-task-name]",
                recoveryManager.getPlan()
                        .getChildren().get(0)
                        .getChildren().get(0)
                        .getName());
        reset(mockDeployManager);
    }

    @Test
    public void failedTasksAreNotLaunchedWithInsufficientResources() throws Exception {
        final double insufficientCpu = TestPodFactory.CPU / 2.;
        final double insufficientMem = TestPodFactory.MEM / 2.;

        final List<Offer> insufficientOffers = getOffers(insufficientCpu, insufficientMem);
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(taskInfo);
        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), status);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        when(mockDeployManager.getCandidates(Collections.emptyList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        final Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(insufficientOffers, planCoordinator.getCandidates());

        assertEquals(0, recommendations.size());
        // Verify we transitioned the task to failed
        // Verify the Task is reported as failed.
        assertNotNull(recoveryManager.getPlan());
        assertNotNull(recoveryManager.getPlan().getChildren());
        assertNotNull(recoveryManager.getPlan().getChildren().get(0).getChildren());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().size() == 1);
        assertEquals("test-task-type-0:[test-task-name]",
                recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).getName());

        reset(mockDeployManager);
    }

    @Test
    public void permanentlyFailedTasksAreRescheduled() throws Exception {
        // Prepare permanently failed task with some reserved resources
        final TaskInfo failedTaskInfo = TaskTestUtils.withFailedFlag(taskInfo);
        final List<TaskInfo> infos = Collections.singletonList(failedTaskInfo);
        final Protos.TaskStatus status = TaskTestUtils.generateStatus(
                failedTaskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        failureMonitor.setFailedList(failedTaskInfo);
        launchConstrainer.setCanLaunch(true);
        stateStore.storeTasks(infos);
        stateStore.storeStatus(taskInfo.getName(), status);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        when(mockDeployManager.getCandidates(Collections.emptyList())).thenReturn(Collections.emptyList());

        recoveryManager.update(status);
        final Collection<OfferRecommendation> recommendations =
                planScheduler.resourceOffers(getOffers(), planCoordinator.getCandidates());

        assertEquals(1, distinctOffers(recommendations).size());

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
        final Protos.TaskStatus runningStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_RUNNING);
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        launchConstrainer.setCanLaunch(true);

        // TASK_RUNNING
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), runningStatus);
        recoveryManager.update(runningStatus);
        assertEquals(0, recoveryManager.getPlan().getChildren().size());

        // TASK_FAILED
        stateStore.storeStatus(taskInfo.getName(), failedStatus);
        recoveryManager.update(failedStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_FAILED
        stateStore.storeStatus(taskInfo.getName(), failedStatus);
        recoveryManager.update(failedStatus);
        assertEquals(1, recoveryManager.getPlan().getChildren().get(0).getChildren().size());
    }

    @Test
    public void testMultipleFailuresSingleTask() throws Exception {
        final Protos.TaskStatus runningStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_RUNNING);
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_FAILED);

        launchConstrainer.setCanLaunch(true);

        // TASK_RUNNING
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), runningStatus);
        recoveryManager.update(runningStatus);
        assertEquals(0, recoveryManager.getPlan().getChildren().size());

        // TASK_FAILED
        stateStore.storeStatus(taskInfo.getName(), failedStatus);
        recoveryManager.update(failedStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_RUNNING
        stateStore.storeTasks(taskInfos);
        stateStore.storeStatus(taskInfo.getName(), runningStatus);
        recoveryManager.update(runningStatus);
        recoveryManager.getCandidates(Collections.emptyList());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());

        // TASK_FAILED
        stateStore.storeStatus(taskInfo.getName(), failedStatus);
        recoveryManager.update(failedStatus);
        assertEquals(1, recoveryManager.getPlan().getChildren().get(0).getChildren().size());
        assertTrue(recoveryManager.getPlan().getChildren().get(0).getChildren().get(0).isPending());
    }

    private static Collection<Protos.OfferID> distinctOffers(Collection<OfferRecommendation> recs) {
        return recs.stream().map(rec -> rec.getOffer().getId()).distinct().collect(Collectors.toList());
    }
}
