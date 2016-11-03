package org.apache.mesos.scheduler.plan;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.scheduler.DefaultTaskKiller;
import org.apache.mesos.scheduler.TaskKiller;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecificationProvider;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Tests for {@code DefaultPlanCoordinator}.
 */
public class DefaultPlanCoordinatorTest {
    private static final String SERVICE_NAME = "test-service-name";
    public static final int SUFFICIENT_CPUS = 2;
    public static final int SUFFICIENT_MEM = 2000;
    public static final int SUFFICIENT_DISK = 10000;
    public static final int INSUFFICIENT_MEM = 1;
    public static final int INSUFFICIENT_DISK = 1;
    private static TestingServer testingServer;

    private List<TaskSet> taskSets;
    private List<TaskSet> taskSetsB;
    private DefaultServiceSpecification serviceSpecification;
    private DefaultServiceSpecification serviceSpecificationB;
    private OfferAccepter offerAccepter;
    private StateStore stateStore;
    private TaskKiller taskKiller;
    private DefaultPlanScheduler planScheduler;
    private TaskFailureListener taskFailureListener;
    private SchedulerDriver schedulerDriver;
    private TaskSpecificationProvider taskSpecificationProvider;
    private StepFactory stepFactory;
    private PhaseFactory phaseFactory;
    private EnvironmentVariables environmentVariables;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void setupTest() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        offerAccepter = spy(new OfferAccepter(Arrays.asList()));
        taskFailureListener = mock(TaskFailureListener.class);
        schedulerDriver = mock(SchedulerDriver.class);
        taskSpecificationProvider = mock(TaskSpecificationProvider.class);
        taskSets = Arrays.asList(TestTaskSetFactory.getTaskSet());
        taskSetsB = Arrays.asList(TestTaskSetFactory.getTaskSet(
                TestConstants.TASK_TYPE + "-B",
                TestTaskSetFactory.COUNT,
                TestTaskSetFactory.CMD.getValue(),
                TestTaskSetFactory.CPU,
                TestTaskSetFactory.MEM,
                TestTaskSetFactory.DISK));
        serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                taskSets);
        stateStore = new CuratorStateStore(
                serviceSpecification.getName(),
                testingServer.getConnectString());
        stepFactory = new DefaultStepFactory(
                mock(ConfigStore.class),
                stateStore,
                new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter(new HashMap<>()), UUID.randomUUID()),
                taskSpecificationProvider);
        phaseFactory = new DefaultPhaseFactory(stepFactory);
        taskKiller = new DefaultTaskKiller(stateStore, taskFailureListener, schedulerDriver);
        planScheduler = new DefaultPlanScheduler(offerAccepter, new OfferEvaluator(stateStore), taskKiller);
        serviceSpecificationB = new DefaultServiceSpecification(
                SERVICE_NAME + "-B",
                taskSetsB);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
    }

    private List<Protos.Offer> getOffers(double cpus, double mem, double disk) {
        final ArrayList<Protos.Offer> offers = new ArrayList<>();
        offers.addAll(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))));
        offers.add(Protos.Offer.newBuilder(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))).get(0))
                .setId(Protos.OfferID.newBuilder().setValue("other-offer"))
                .build());
        return offers;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoPlanManager() {
        new DefaultPlanCoordinator(Arrays.asList(), planScheduler);
    }

    @Test
    public void testOnePlanManagerPendingSufficientOffer() throws Exception {
        final Plan plan = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan)), planScheduler);
        Assert.assertEquals(1, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testOnePlanManagerPendingInSufficientOffer() throws Exception {
        final Plan plan = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan)), planScheduler);
        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                INSUFFICIENT_MEM, INSUFFICIENT_DISK)).size());
    }

    @Test
    public void testOnePlanManagerComplete() throws Exception {
        final Plan plan = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        ((Step) plan.getChildren().get(0).getChildren().get(0)).forceComplete();
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan)), planScheduler);
        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansDisjointAssets() throws Exception {
        final Plan planA = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecificationB);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA);
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);
        Assert.assertEquals(2, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssets() throws Exception {
        final Plan planA = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final PlanManager planManagerA = new DefaultPlanManager(planA);
        final PlanManager planManagerB = new DefaultPlanManager(planB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB),
                planScheduler);

        Assert.assertEquals(
                1,
                coordinator.processOffers(
                        schedulerDriver,
                        getOffers(
                                SUFFICIENT_CPUS,
                                SUFFICIENT_MEM,
                                SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersCompletePlans() throws Exception {
        final Plan planA = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA);
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB);

        planA.getChildren().get(0).getChildren().get(0).forceComplete();
        planB.getChildren().get(0).getChildren().get(0).forceComplete();

        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);

        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssetsDifferentOrder() throws Exception {
        final Plan planA = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final PlanManager planManagerA = new DefaultPlanManager(planA);
        final PlanManager planManagerB = new DefaultPlanManager(planB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB),
                planScheduler);

        planB.getChildren().get(0).getChildren().get(0).setStatus(Status.IN_PROGRESS);

        // PlanA and PlanB have similar asset names. PlanA is configured to run before PlanB.
        // In a given offer cycle, PlanA's asset is PENDING, where as PlanB's asset is already in IN_PROGRESS.
        // PlanCoordinator should ensure that PlanA PlanManager knows about PlanB's (and any other configured plan's)
        // dirty assets.
        Assert.assertEquals(
                0,
                coordinator.processOffers(
                        schedulerDriver,
                        getOffers(
                                SUFFICIENT_CPUS,
                                SUFFICIENT_MEM,
                                SUFFICIENT_DISK)).size());
    }
}
