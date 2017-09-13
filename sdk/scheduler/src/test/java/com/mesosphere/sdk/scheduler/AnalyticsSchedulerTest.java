package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.TerminalStrategy;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.testutils.PlanTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.*;

public class AnalyticsSchedulerTest extends DefaultSchedulerTest {

    private AnalyticsScheduler scheduler;

    @Captor
    private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;

    private static final int WORKER_COUNT = 2;

    private static final TaskSpec workTask = TestPodFactory.getTaskSpec(
            "workTask",
            "compute-something",
            null,
            GoalState.FINISHED,
            TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID + "work", TASK_A_CPU, TASK_A_MEM, TASK_A_DISK),
            Collections.emptyList());

    private static final TaskSpec driverTask = TestPodFactory.getTaskSpec(
            "driver",
            "drive-something",
            null,
            GoalState.FINISHED,
            TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID + "driver", TASK_A_CPU, TASK_A_MEM, TASK_A_DISK),
            Collections.emptyList());

    private static final PodSpec podA = TestPodFactory.getPodSpec(
            "driver",
            Collections.singletonList(driverTask),
            TestConstants.SERVICE_USER,
            1);

    private static final PodSpec podB = TestPodFactory.getPodSpec(
            "worker",
            Collections.singletonList(workTask),
            TestConstants.SERVICE_USER,
            WORKER_COUNT);

    public void initScheduler() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockSchedulerFlags.isStateCacheEnabled()).thenReturn(true);
        ServiceSpec serviceSpec = getServiceSpec(podA, podB);
        stateStore = new StateStore(new PersisterCache(new MemPersister()));
        configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), new MemPersister());
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        scheduler = AnalyticsScheduler.newBuilder(serviceSpec, flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        scheduler = new TestScheduler(scheduler, true);
        register();
    }

    private void statusUpdate(Protos.TaskID launchedTaskId, Protos.TaskState state) {
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, state);
        scheduler.statusUpdate(mockSchedulerDriver, runningStatus);
    }

    private Collection<Protos.Offer.Operation> sendOffer(AnalyticsScheduler scheduler) {
        Protos.Offer offer = getSufficientOfferForTaskA();

        List<Protos.Offer> offers = Arrays.asList(offer);
        Protos.OfferID offerId = offer.getId();

        // Offer sufficient Resource and wait for its acceptance
        scheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                Matchers.argThat(isACollectionThat(contains(offerId))),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(8, operations.size());
        Assert.assertEquals(6, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH_GROUP, operations));
        return operations;
    }

    private Protos.TaskID launchStep(
            AnalyticsScheduler scheduler, int phaseIndex, int stepIndex, Protos.Offer offer, boolean finishStep) {
        // Get first Step associated with Task A-0
        Plan plan = scheduler.deploymentPlanManager.getPlan();
        List<Protos.Offer> offers = Arrays.asList(offer);
        Protos.OfferID offerId = offer.getId();
        Step step = plan.getChildren().get(phaseIndex).getChildren().get(stepIndex);
        Assert.assertTrue(step.isPending());

        // Offer sufficient Resource and wait for its acceptance
        scheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                Matchers.argThat(isACollectionThat(contains(offerId))),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(8, operations.size());
        Assert.assertEquals(6, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH_GROUP, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(step).isStarting(), equalTo(true));

        // Sent TASK_RUNNING status
        Protos.TaskID taskId = getTaskId(operations);
        statusUpdate(getTaskId(operations), (finishStep ? Protos.TaskState.TASK_FINISHED : Protos.TaskState.TASK_RUNNING));

        // Wait for the Step to become running
        if (finishStep) {
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(Awaitility.to(step).isComplete(), equalTo(true));
        } else {
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(Awaitility.to(step).isRunning(), equalTo(true));
        }

        return taskId;
    }

    private Protos.TaskID launchStep(int phaseIndex, int stepIndex, Protos.Offer offer, boolean finishStep) {
        return launchStep(scheduler, phaseIndex, stepIndex, offer, finishStep);
    }

    private List<Protos.TaskID> install() throws InterruptedException {
        List<Protos.TaskID> taskIds = new ArrayList<>();

        Plan plan = scheduler.deploymentPlanManager.getPlan();
        taskIds.add(launchStep(0, 0, getSufficientOfferForTaskA(), true));
        scheduler.awaitOffersProcessed();

        Assert.assertTrue(scheduler.deploymentPlanManager.getPlan().isComplete());
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE),
                PlanTestUtils.getStepStatuses(plan));
        Awaitility.await()
                .atMost(
                        SuppressReviveManager.SUPPRESSS_REVIVE_DELAY_S +
                                SuppressReviveManager.SUPPRESSS_REVIVE_INTERVAL_S + 1,
                        TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return StateStoreUtils.isSuppressed(stateStore);
                    }
                });

        return taskIds;
    }

    private static class TestScheduler extends AnalyticsScheduler {
        private final boolean apiServerReady;

        public TestScheduler(AnalyticsScheduler scheduler, boolean apiServerReady) {
            super(
                    scheduler.serviceSpec,
                    flags,
                    scheduler.resources,
                    scheduler.plans,
                    scheduler.stateStore,
                    scheduler.configStore,
                    scheduler.customEndpointProducers,
                    scheduler.recoveryPlanOverriderFactory,
                    scheduler.deregisterStep);
            this.apiServerReady = apiServerReady;
        }

        public boolean apiServerReady() {
            return apiServerReady;
        }
    }

    @Test
    public void testSchedulerConstruction() throws Exception {
        initScheduler();
        Assert.assertNotNull(scheduler);
    }

    @Test
    public void testEmptyOffersAreNotAccepted() throws Exception {
        initScheduler();
        scheduler.resourceOffers(mockSchedulerDriver, Collections.emptyList());
        verify(mockSchedulerDriver, times(1)).reconcileTasks(any());
        verify(mockSchedulerDriver, times(0)).acceptOffers(any(), anyCollectionOf(Protos.Offer.Operation.class), any());
        verify(mockSchedulerDriver, times(0)).declineOffer(any(), any());
    }

    @Test
    public void testDeregisterWaits() throws Exception {
        initScheduler();
        launchStep(0, 0, getSufficientOfferForTaskA(), true);
        launchStep(1, 0, getSufficientOfferForTaskA(), true);
        Protos.TaskID penultimateTask = launchStep(1, 1, getSufficientOfferForTaskA(), false);
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.STARTING, Status.PENDING),
                PlanTestUtils.getStepStatuses(scheduler.deploymentPlanManager.getPlan()));
        statusUpdate(penultimateTask, Protos.TaskState.TASK_FINISHED);
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING),
                PlanTestUtils.getStepStatuses(scheduler.deploymentPlanManager.getPlan()));
    }

    @Test
    public void validAnalytics() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-analytics.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        stateStore = new StateStore(new PersisterCache(new MemPersister()));
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), new MemPersister());
        scheduler = AnalyticsScheduler.newBuilder(serviceSpec, flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .setPlansFrom(RawServiceSpec.newBuilder(file).build())
                .build();
        scheduler = new TestScheduler(scheduler, true);
        scheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
        DefaultPlan plan = (DefaultPlan) scheduler.deploymentPlanManager.getPlan();
        TerminalStrategy<Phase> terminalStrategy = (TerminalStrategy<Phase>) plan.getStrategy();
        // Check that parallel flag is propagated through from yaml
        Assert.assertTrue(terminalStrategy.isParallel());
        // Check that terminal phase is added
        Assert.assertTrue(plan.getPhases().size() == 3);
        Assert.assertTrue(plan.getPhases().get(2).getName().equals("auto-delete"));
        Assert.assertTrue(terminalStrategy.getCandidates(plan.getChildren(),
                Collections.emptyList()).size() == 2);
        // Check that statuses are all pending
        List<Status> statuses = PlanTestUtils.getStepStatuses(scheduler.deploymentPlanManager.getPlan());
        Assert.assertTrue("", statuses.size() == 4);
        Set<Status> obs = new HashSet<>(statuses);
        Set<Status> expected = new HashSet<>(Arrays.asList(Status.PENDING));
        Assert.assertEquals("", obs, expected);
        // Send offers for the driver, and the two workers, make sure they are accepted
        Collection<Protos.Offer.Operation> op0 = sendOffer(scheduler);
        Collection<Protos.Offer.Operation> op1 = sendOffer(scheduler);
        Collection<Protos.Offer.Operation> op2 = sendOffer(scheduler);
        statuses = PlanTestUtils.getStepStatuses(scheduler.deploymentPlanManager.getPlan());
        Assert.assertTrue(String.format("Got statuses %s, should have 1 PENDING", statuses.toString()),
                statuses.stream().filter(status -> status == Status.PENDING).count() == 1);
        // Verify that the scheduler declines offers now
        Protos.Offer offer = getSufficientOfferForTaskA();
        List<Protos.Offer> offers = Arrays.asList(offer);
        Protos.OfferID offerId = offer.getId();
        scheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, timeout(3000).times(1))
                .declineOffer(offerId);
        statuses = PlanTestUtils.getStepStatuses(scheduler.deploymentPlanManager.getPlan());
        Assert.assertTrue("", statuses.stream()
                .filter(status -> status == Status.PENDING).count() == 1);
        // Update the tasks so they are finished, this is when we'd want to clean up the service
        statusUpdate(getTaskId(op0), Protos.TaskState.TASK_FINISHED);
        statusUpdate(getTaskId(op1), Protos.TaskState.TASK_FINISHED);
        statusUpdate(getTaskId(op2), Protos.TaskState.TASK_FINISHED);
        // Send a final offer to "spin the freewheel" it will be declined because the final step ("deregister step")
        // is NOOP
        Protos.Offer finalOffer = getSufficientOfferForTaskA();
        List<Protos.Offer> finalOffers= Arrays.asList(finalOffer);
        // Verify that the scheduler declines offers now
        scheduler.resourceOffers(mockSchedulerDriver, finalOffers);
        verify(mockSchedulerDriver, timeout(3000).times(1))
                .declineOffer(finalOffer.getId());
        statuses = PlanTestUtils.getStepStatuses(plan);
        // Assert all of the steps are now complete
        Assert.assertTrue(String.format("Got Statuses %s", statuses),
                statuses.stream().allMatch(status -> status == Status.COMPLETE));

    }




    private void register() {
        scheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
    }
}
