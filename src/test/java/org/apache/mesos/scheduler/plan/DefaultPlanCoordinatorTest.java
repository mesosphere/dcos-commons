package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.ChainedObserver;
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
    public static final int SUFFICIENT_CPUS = 2;
    public static final int SUFFICIENT_MEM = 2000;
    public static final int SUFFICIENT_DISK = 10000;
    public static final int INSUFFICIENT_MEM = 1;
    public static final int INSUFFICIENT_DISK = 1;

    List<TaskSet> taskSets;
    List<TaskSet> taskSetsB;
    DefaultServiceSpecification serviceSpecification;
    DefaultServiceSpecification serviceSpecificationB;
    OfferAccepter offerAccepter;
    OfferRequirementProvider offerRequirementProvider;
    StateStore stateStore;
    TaskKiller taskKiller;
    DefaultPlanScheduler planScheduler;
    TaskFailureListener taskFailureListener;
    SchedulerDriver schedulerDriver;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        offerAccepter = spy(new OfferAccepter(Arrays.asList()));
        offerRequirementProvider = new DefaultOfferRequirementProvider();
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
        when(stateStore.fetchTask(anyString())).thenReturn(Optional.empty());
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
        final Plan plan = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertEquals(1, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testOnePlanManagerPendingInSufficientOffer() throws Exception {
        final Plan plan = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                INSUFFICIENT_MEM, INSUFFICIENT_DISK)).size());
    }

    @Test
    public void testOnePlanManagerComplete() throws Exception {
        final Plan plan = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        plan.getPhases().get(0).getBlocks().get(0).forceComplete();
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(new DefaultPlanManager(plan, new DefaultStrategyFactory())),
                planScheduler);
        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansDisjointAssets() throws Exception {
        final Plan planA = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecificationB);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA, new DefaultStrategyFactory());
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB, new DefaultStrategyFactory());
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);
        Assert.assertEquals(2, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersPendingPlansSameAssets() throws Exception {
        final Plan planA = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final PlanManager planManagerA = new TestingPlanManager();
        planManagerA.setPlan(planA);
        final PlanManager planManagerB = new TestingPlanManager();
        planManagerB.setPlan(planB);
        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);
        Assert.assertEquals(1, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    @Test
    public void testTwoPlanManagersCompletePlans() throws Exception {
        final Plan planA = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final Plan planB = new DefaultPlanFactory(stateStore, offerRequirementProvider).getPlan(serviceSpecification);
        final DefaultPlanManager planManagerA = new DefaultPlanManager(planA, new DefaultStrategyFactory());
        final DefaultPlanManager planManagerB = new DefaultPlanManager(planB, new DefaultStrategyFactory());

        planA.getPhases().get(0).getBlocks().get(0).forceComplete();
        planB.getPhases().get(0).getBlocks().get(0).forceComplete();

        final DefaultPlanCoordinator coordinator = new DefaultPlanCoordinator(
                Arrays.asList(planManagerA, planManagerB), planScheduler);

        Assert.assertEquals(0, coordinator.processOffers(schedulerDriver, getOffers(SUFFICIENT_CPUS,
                SUFFICIENT_MEM, SUFFICIENT_DISK)).size());
    }

    public static class TestingPlanManager extends ChainedObserver implements PlanManager {
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
        public Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets) {
            final List<? extends Block> blocks = plan.getPhases().get(0).getBlocks();
            List<Block> filtered = new ArrayList<>();
            for (Block block: blocks) {
                if (!dirtiedAssets.contains(block.getName())) {
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
        public Set<String> getDirtyAssets() {
            return new HashSet<>();
        }
    }
}