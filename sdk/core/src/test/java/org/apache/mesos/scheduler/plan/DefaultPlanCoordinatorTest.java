package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.scheduler.DefaultTaskKiller;
import org.apache.mesos.scheduler.TaskKiller;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

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
    private BlockFactory blockFactory;
    private PhaseFactory phaseFactory;
    private EnvironmentVariables environmentVariables;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        offerAccepter = spy(new OfferAccepter(Arrays.asList()));
        stateStore = mock(StateStore.class);
        taskFailureListener = mock(TaskFailureListener.class);
        schedulerDriver = mock(SchedulerDriver.class);
        blockFactory = new DefaultBlockFactory(stateStore);
        phaseFactory = new DefaultPhaseFactory(blockFactory);
        taskKiller = new DefaultTaskKiller(stateStore, taskFailureListener, schedulerDriver);
        planScheduler = new DefaultPlanScheduler(offerAccepter, new OfferEvaluator(stateStore), taskKiller);
        taskSets = Arrays.asList(TestTaskSetFactory.getTaskSet());
        taskSetsB = Arrays.asList(TestTaskSetFactory.getTaskSet(
                TestTaskSetFactory.NAME + "-B",
                TestTaskSetFactory.COUNT,
                TestTaskSetFactory.CMD.getValue(),
                TestTaskSetFactory.CPU,
                TestTaskSetFactory.MEM,
                TestTaskSetFactory.DISK));
        serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                taskSets);
        serviceSpecificationB = new DefaultServiceSpecification(
                SERVICE_NAME + "-B",
                taskSetsB);
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
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
        ((Block) plan.getChildren().get(0).getChildren().get(0)).forceComplete();
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

        ((Block) planA.getChildren().get(0).getChildren().get(0)).forceComplete();
        ((Block) planB.getChildren().get(0).getChildren().get(0)).forceComplete();

        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);

        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }
}