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
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@code DefaultPlanCoordinator}.
 */
public class DefaultPlanCoordinatorTest {
    private static final String SERVICE_NAME = "test-service-name";

    List<TaskSet> taskSets;
    List<TaskSet> taskSetsB;
    DefaultServiceSpecification serviceSpecification;
    DefaultServiceSpecification serviceSpecificationB;
    OfferAccepter offerAccepter;
    StateStore stateStore;
    TaskKiller taskKiller;
    DefaultPlanScheduler planScheduler;
    TaskFailureListener taskFailureListener;
    SchedulerDriver schedulerDriver;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        offerAccepter = spy(new OfferAccepter(Arrays.asList()));
        stateStore = mock(StateStore.class);
        taskFailureListener = mock(TaskFailureListener.class);
        schedulerDriver = mock(SchedulerDriver.class);
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
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(), planScheduler);
        coordinator.processOffers(schedulerDriver, getOffers(1, 1, 1));
    }

    @Test
    public void testOnePlanManagerPendingSufficientOffer() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan plan = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertTrue(coordinator.processOffers(schedulerDriver, getOffers(2, 2000, 10000)).size() == 1);
    }

    @Test
    public void testOnePlanManagerPendingInSufficientOffer() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan plan = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertTrue(coordinator.processOffers(schedulerDriver, getOffers(2, 1, 1)).size() == 0);
    }

    @Test
    public void testOnePlanManagerComplete() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan plan = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        plan.getPhases().get(0).getBlocks().get(0).forceComplete();
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertTrue(coordinator.processOffers(schedulerDriver, getOffers(2, 2000, 10000)).size() == 0);
    }

    @Test
    public void testTwoPlanManagersPendingPlansDisjointAssets() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan planA = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore).getPlan(serviceSpecificationB);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA, new DefaultStrategyFactory());
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB, new DefaultStrategyFactory());
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);
        Assert.assertEquals(2, coordinator.processOffers(schedulerDriver, getOffers(2, 2000, 10000)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssets() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan planA = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final PlanManager planManagerA = new TestingPlanManager();
        planManagerA.setPlan(planA);
        final PlanManager planManagerB = new TestingPlanManager();
        planManagerB.setPlan(planB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);
        Assert.assertEquals(1, coordinator.processOffers(schedulerDriver, getOffers(2, 2000, 10000)).size());
    }

    @Test
    public void testTwoPlanManagersCompletePlans() throws Exception {
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        final Plan planA = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA, new DefaultStrategyFactory());
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB, new DefaultStrategyFactory());

        planA.getPhases().get(0).getBlocks().get(0).forceComplete();
        planB.getPhases().get(0).getBlocks().get(0).forceComplete();

        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);

        Assert.assertTrue(coordinator.processOffers(schedulerDriver, getOffers(2, 2000, 10000)).size() == 0);
    }

    @Test
    public void testFilterAcceptedOffers() {
        final List<Protos.Offer> offers = getOffers(2, 2000, 10000);
        final Protos.Offer acceptedOffer = offers.get(0);
        final Protos.Offer unAcceptedOffer = offers.get(1);
        final List<Protos.Offer> unacceptedOffers = DefaultPlanCoordinator
                .filterAcceptedOffers(offers, Arrays.asList(acceptedOffer.getId()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(1, unacceptedOffers.size());
        Assert.assertEquals(unAcceptedOffer.getId(), unacceptedOffers.get(0).getId());
    }

    @Test
    public void testFilterAcceptedOffersNoAccepted() {
        final List<Protos.Offer> offers = getOffers(2, 2000, 10000);
        final List<Protos.Offer> unacceptedOffers = DefaultPlanCoordinator
                .filterAcceptedOffers(offers, Arrays.asList());
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAllAccepted() {
        final List<Protos.Offer> offers = getOffers(2, 2000, 10000);
        final List<Protos.Offer> unacceptedOffers = DefaultPlanCoordinator
                .filterAcceptedOffers(offers, Arrays.asList(offers.get(0).getId(), offers.get(1).getId()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(0, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAcceptedInvalidId() {
        final List<Protos.Offer> offers = getOffers(2, 2000, 10000);
        final List<Protos.Offer> unacceptedOffers = DefaultPlanCoordinator
                .filterAcceptedOffers(offers, Arrays.asList(Protos.OfferID.newBuilder().setValue("abc").build()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    public static class TestingPlanManager implements PlanManager {
        Plan plan;

        @Override
        public Plan getPlan() {
            return plan;
        }

        @Override
        public void setPlan(Plan plan) {
            this.plan = plan;
        }

        @Override
        public Optional<Phase> getCurrentPhase() {
            return Optional.of(plan.getPhases().get(0));
        }

        @Override
        public Optional<Block> getCurrentBlock(List<Block> dirtiedAssets) {
            final List<? extends Block> blocks = plan.getPhases().get(0).getBlocks();
            Set<String> dirtyNames = new HashSet<>();
            dirtiedAssets.stream().forEach(b -> dirtyNames.add(b.getName()));
            List<Block> filtered = new ArrayList<>();
            for (Block block: blocks) {
                if (!dirtyNames.contains(block.getName())) {
                    filtered.add(block);
                }
            }
            return filtered.size() > 0 ? Optional.of(filtered.get(0)) : Optional.empty();
        }

        @Override
        public boolean isComplete() {
            return plan.isComplete();
        }

        @Override
        public void proceed() {

        }

        @Override
        public void interrupt() {

        }

        @Override
        public boolean isInterrupted() {
            return false;
        }

        @Override
        public void restart(UUID phaseId, UUID blockId) {

        }

        @Override
        public void forceComplete(UUID phaseId, UUID blockId) {

        }

        @Override
        public void update(Protos.TaskStatus status) {

        }

        @Override
        public boolean hasDecisionPoint(Block block) {
            return false;
        }

        @Override
        public Status getStatus() {
            return Status.PENDING;
        }

        @Override
        public Status getPhaseStatus(UUID phaseId) {
            return null;
        }

        @Override
        public List<String> getErrors() {
            return Arrays.asList();
        }

        @Override
        public void update(Observable o, Object arg) {

        }
    }
}