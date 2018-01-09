package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.OfferAccepter;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@code DefaultPlanCoordinator}.
 */
public class DefaultPlanCoordinatorTest extends DefaultCapabilitiesTestSuite {

    private static final String SERVICE_NAME = "test-service-name";
    public static final int SUFFICIENT_CPUS = 2;
    public static final int SUFFICIENT_MEM = 2000;
    public static final int SUFFICIENT_DISK = 10000;
    public static final int INSUFFICIENT_MEM = 1;
    public static final int INSUFFICIENT_DISK = 1;

    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_POD_NAME = "POD-A";
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final double TASK_A_DISK = 1500.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_POD_NAME = "POD-B";
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final double TASK_B_DISK = 2500.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static final Protos.OfferID OTHER_ID = Protos.OfferID.newBuilder().setValue("other-offer").build();

    private static final PodSpec podA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT,
            TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static final PodSpec podB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT,
            TASK_B_CPU,
            TASK_B_MEM,
            TASK_B_DISK);

    private DefaultServiceSpec serviceSpecification;
    private DefaultServiceSpec serviceSpecificationB;
    private StateStore stateStore;
    private DefaultPlanScheduler planScheduler;
    private SchedulerDriver schedulerDriver;
    private StepFactory stepFactory;
    private PhaseFactory phaseFactory;

    @Before
    public void setupTest() throws Exception {
        MockitoAnnotations.initMocks(this);

        schedulerDriver = mock(SchedulerDriver.class);
        serviceSpecification = DefaultServiceSpec.newBuilder()
                .name(SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("foo.bar.com")
                .pods(Arrays.asList(podA))
                .build();
        stateStore = new StateStore(new MemPersister());
        stateStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        stepFactory = new DefaultStepFactory(mock(ConfigStore.class), stateStore);
        phaseFactory = new DefaultPhaseFactory(stepFactory);

        planScheduler = new DefaultPlanScheduler(
                new OfferAccepter(Arrays.asList()),
                new OfferEvaluator(
                        stateStore,
                        new OfferOutcomeTracker(),
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        true),
                stateStore,
                new TaskKiller(schedulerDriver));
        serviceSpecificationB = DefaultServiceSpec.newBuilder()
                .name(SERVICE_NAME + "-B")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("foo.bar.com")
                .pods(Arrays.asList(podB))
                .build();
    }

    private List<Protos.Offer> getOffers(double cpus, double mem, double disk) {
        final ArrayList<Protos.Offer> offers = new ArrayList<>();
        offers.addAll(OfferTestUtils.getCompleteOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpus(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))));
        offers.add(Protos.Offer.newBuilder(OfferTestUtils.getCompleteOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpus(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))).get(0))
                .setId(OTHER_ID)
                .build());
        return offers;
    }

    private PodInstanceRequirement getPodInstanceRequirement(PodSpec podSpec, int index) {
        PodInstance podInstance = new DefaultPodInstance(podSpec, index);
        return PodInstanceRequirement.newBuilder(
                podInstance,
                podSpec.getTasks().stream().map(TaskSpec::getName).collect(Collectors.toList()))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoPlanManager() {
        new DefaultPlanCoordinator(Arrays.asList());
    }

    @Test
    public void testOnePlanManagerPendingSufficientOffer() throws Exception {
        final Plan plan = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final PlanManager planManager = DefaultPlanManager.createProceeding(plan);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(planManager));
        Assert.assertEquals(
                Arrays.asList(TestConstants.OFFER_ID),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testPodInstanceRequirementConflictsWith() {
        PodSpec pod = TestPodFactory.getMultiTaskPodSpec(
                TASK_A_POD_NAME,
                TestConstants.RESOURCE_SET_ID + "-A",
                TASK_A_NAME,
                TASK_A_CMD,
                TestConstants.SERVICE_USER,
                TASK_A_COUNT,
                TASK_A_CPU,
                TASK_A_MEM,
                TASK_A_DISK,
                2);
        PodSpec podOverlapTask = TestPodFactory.getMultiTaskPodSpec(
                TASK_A_POD_NAME,
                TestConstants.RESOURCE_SET_ID + "-A",
                TASK_A_NAME,
                TASK_A_CMD,
                TestConstants.SERVICE_USER,
                TASK_A_COUNT,
                TASK_A_CPU,
                TASK_A_MEM,
                TASK_A_DISK,
                1);
        PodSpec podDifferentTask = TestPodFactory.getMultiTaskPodSpec(
                TASK_A_POD_NAME,
                TestConstants.RESOURCE_SET_ID + "-A",
                "AA",
                TASK_A_CMD,
                TestConstants.SERVICE_USER,
                TASK_A_COUNT,
                TASK_A_CPU,
                TASK_A_MEM,
                TASK_A_DISK,
                1);
        PodSpec podDifferentIndex = TestPodFactory.getMultiTaskPodSpec(
                TASK_B_POD_NAME,
                TestConstants.RESOURCE_SET_ID + "-A",
                TASK_A_NAME,
                TASK_A_CMD,
                TestConstants.SERVICE_USER,
                TASK_A_COUNT,
                TASK_A_CPU,
                TASK_A_MEM,
                TASK_A_DISK,
                2);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(pod, 0);
        PodInstanceRequirement conflictsOverlapTasks = getPodInstanceRequirement(podOverlapTask, 0);
        PodInstanceRequirement noConflictDifferentTasks = getPodInstanceRequirement(podDifferentTask, 0);
        PodInstanceRequirement noConflictDifferentIndex = getPodInstanceRequirement(podDifferentIndex, 0);
        // pods with overlapping tasks conflict
        Assert.assertTrue(podInstanceRequirement.conflictsWith(conflictsOverlapTasks));
        // pods with different tasks do NOT conflict
        Assert.assertFalse(podInstanceRequirement.conflictsWith(noConflictDifferentTasks));
        // pods with different indices, but the same tasks do not conflict
        Assert.assertFalse(podInstanceRequirement.conflictsWith(noConflictDifferentIndex));
        // a pod conflicts with itseld
        Assert.assertTrue(podInstanceRequirement.conflictsWith(podInstanceRequirement));
    }

    @Test
    public void testOnePlanManagerPendingInSufficientOffer() throws Exception {
        final Plan plan = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(DefaultPlanManager.createInterrupted(plan)));
        Assert.assertEquals(
                Collections.emptyList(),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, INSUFFICIENT_MEM, INSUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testOnePlanManagerComplete() throws Exception {
        final Plan plan = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        plan.getChildren().get(0).getChildren().get(0).forceComplete();
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(DefaultPlanManager.createInterrupted(plan)));
        Assert.assertEquals(
                Collections.emptyList(),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testTwoPlanManagersPendingPlansDisjointAssets() throws Exception {
        final Plan planA = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecificationB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(DefaultPlanManager.createProceeding(planA), DefaultPlanManager.createProceeding(planB)));
        Assert.assertEquals(
                Arrays.asList(TestConstants.OFFER_ID, OTHER_ID),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssets() throws Exception {
        final Plan planA = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        ServiceSpec serviceSpecB = DefaultServiceSpec.newBuilder(serviceSpecification)
                .name(serviceSpecification.getName() + "-B")
                .build();
        final Plan planB = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(DefaultPlanManager.createProceeding(planA), DefaultPlanManager.createProceeding(planB)));
        Assert.assertEquals(
                Arrays.asList(TestConstants.OFFER_ID),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testTwoPlanManagersCompletePlans() throws Exception {
        final Plan planA = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanManager planManagerA = DefaultPlanManager.createInterrupted(planA);
        final DefaultPlanManager planManagerB = DefaultPlanManager.createInterrupted(planB);

        planA.getChildren().get(0).getChildren().get(0).forceComplete();
        planB.getChildren().get(0).getChildren().get(0).forceComplete();

        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB));
        Assert.assertEquals(
                Collections.emptyList(),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssetsDifferentOrder() throws Exception {
        final Plan planA = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final Plan planB = new DeployPlanFactory(phaseFactory).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(DefaultPlanManager.createProceeding(planA), DefaultPlanManager.createProceeding(planB)));

        Assert.assertTrue(planA.getChildren().get(0).getChildren().get(0).getStatus().equals(Status.PENDING));
        ((DeploymentStep) planB.getChildren().get(0).getChildren().get(0)).setStatus(Status.PREPARED);

        // PlanA and PlanB have similar asset names. PlanA is configured to run before PlanB.
        // In a given offer cycle, PlanA's asset is PENDING, where as PlanB's asset is already in PREPARED.
        // PlanCoordinator should ensure that PlanA PlanManager knows about PlanB's (and any other configured plan's)
        // dirty assets.
        Assert.assertEquals(
                Arrays.asList(TestConstants.OFFER_ID),
                planScheduler.resourceOffers(
                        schedulerDriver,
                        getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK),
                        coordinator.getCandidates()));

        Assert.assertTrue(planB.getChildren().get(0).getChildren().get(0).getStatus().equals(Status.STARTING));
        Assert.assertTrue(planA.getChildren().get(0).getChildren().get(0).getStatus().equals(Status.PENDING));
    }
}
