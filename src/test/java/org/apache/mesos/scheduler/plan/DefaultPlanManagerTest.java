package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * This class tests the {@link DefaultPlanManager}.
 */
public class DefaultPlanManagerTest {

    private TestBlock firstBlock, secondBlock;
    private Plan plan;
    private PhaseStrategyFactory stratFactory;
    private PlanManager planManager;

    @Mock
    Block mockBlock;

    @Mock
    Reconciler reconciler;

    @Before
    public void beforeEach() {
        firstBlock = new TestBlock();
        secondBlock = new TestBlock();
        plan = getTestStage(firstBlock, secondBlock);
        stratFactory = new DefaultStrategyFactory();
        planManager = new DefaultPlanManager(plan, stratFactory);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetStage() {
        Assert.assertEquals(2, planManager.getPlan().getPhases().size());
        planManager.setPlan(getEmptyStage());
        Assert.assertEquals(0, planManager.getPlan().getPhases().size());
        planManager.setPlan(getTestStage(firstBlock, secondBlock));
        Assert.assertEquals(2, planManager.getPlan().getPhases().size());
    }

    @Test
    public void testGetCurrentPhase() {
        Phase firstPhase = plan.getPhases().get(0);
        Phase secondPhase = plan.getPhases().get(1);
        Assert.assertEquals(firstPhase, planManager.getCurrentPhase().get());

        completePhase(firstPhase);
        Assert.assertEquals(secondPhase, planManager.getCurrentPhase().get());

        completePhase(secondPhase);
        Assert.assertFalse(planManager.getCurrentPhase().isPresent());
    }

    @Test
    public void testGetPhaseStatus() {
        Phase firstPhase = plan.getPhases().get(0);
        Assert.assertEquals(Status.PENDING, planManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertEquals(Status.COMPLETE, planManager.getPhaseStatus(firstPhase.getId()));
        // bad id:
        Assert.assertEquals(Status.ERROR, planManager.getPhaseStatus(UUID.randomUUID()));
    }


    @Test
    public void testEmptyStageStatus() {
        PlanManager emptyManager = new DefaultPlanManager(getEmptyStage(), stratFactory);
        Assert.assertEquals(Status.COMPLETE, emptyManager.getStatus());
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.PENDING, planManager.getStatus());
        firstBlock.setStatus(Status.ERROR);
        Assert.assertNull(planManager.getStatus());
        firstBlock.setStatus(Status.WAITING);
        Assert.assertNull(planManager.getStatus()); // error state: Blocks shouldn't be WAITING
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getStatus());
        completePhase(plan.getPhases().get(0));
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getStatus());
        completePhase(plan.getPhases().get(1));
        Assert.assertEquals(Status.COMPLETE, planManager.getStatus());
    }

    @Test
    public void testInProgressStatus() {
        when(reconciler.isReconciled()).thenReturn(false);
        ReconciliationPhase reconciliationPhase = ReconciliationPhase.create(reconciler);
        Plan waitingPlan = DefaultPlan.fromArgs(
                reconciliationPhase,
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-1",
                        Arrays.asList(secondBlock)));

        Block reconciliationBlock = reconciliationPhase.getBlock(0);
        reconciliationBlock.start();
        Assert.assertTrue(reconciliationBlock.isInProgress());

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan, new StageStrategyFactory());
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getStatus());
    }

    @Test
    public void testGetCurrentBlock() {
        Assert.assertEquals(plan.getPhases().get(0).getBlock(0), planManager.getCurrentBlock().get());
    }

    @Test
    public void testIsComplete() {
        Assert.assertFalse(planManager.isComplete());
        completePhase(plan.getPhases().get(0));
        Assert.assertFalse(planManager.isComplete());
        completePhase(plan.getPhases().get(1));
        Assert.assertTrue(planManager.isComplete());
    }

    @Test
    public void testInterruptProceed() {
        Assert.assertFalse(planManager.isInterrupted());
        planManager.interrupt();
        Assert.assertTrue(planManager.isInterrupted());
        planManager.proceed();
        Assert.assertFalse(planManager.isInterrupted());
    }

    @Test
    public void testRestart() {
        Phase firstPhase = plan.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstBlock.isComplete());
        planManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());
        planManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testRestartBadIds() {
        Phase firstPhase = plan.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstBlock.isComplete());
        planManager.restart(firstPhase.getId(), UUID.randomUUID()); // bad block
        Assert.assertTrue(firstBlock.isComplete()); // no change
        planManager.restart(UUID.randomUUID(), firstBlock.getId()); // bad phase
        Assert.assertTrue(firstBlock.isComplete()); // no change
        planManager.restart(firstPhase.getId(), firstBlock.getId()); // correct
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testForceComplete() {
        Phase firstPhase = plan.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        planManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());
        planManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testForceCompleteBadIds() {
        Phase firstPhase = plan.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        planManager.forceComplete(firstPhase.getId(), UUID.randomUUID()); // bad block
        Assert.assertTrue(firstBlock.isPending()); // no change
        planManager.forceComplete(UUID.randomUUID(), firstBlock.getId()); // bad phase
        Assert.assertTrue(firstBlock.isPending()); // no change
        planManager.forceComplete(firstPhase.getId(), firstBlock.getId()); // correct
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testUpdate() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Plan mockPlan = DefaultPlan.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        PlanManager mockPlanManager = new DefaultPlanManager(mockPlan, stratFactory);
        Protos.TaskStatus testStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();

        verify(mockBlock, times(0)).update(any());
        mockPlanManager.update(testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testUpdateObserver() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Plan mockPlan = DefaultPlan.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        Protos.TaskStatus testStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();
        PlanManager mockPlanManager = new DefaultPlanManager(mockPlan, stratFactory);

        verify(mockBlock, times(0)).update(any());
        mockPlanManager.update(null, testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testHasDecisionPoint() {
        Block firstBlock = plan.getPhases().get(0).getBlock(0);

        PlanManager decisionPointManager = new DefaultPlanManager(plan, new StageStrategyFactory());
        Assert.assertTrue(decisionPointManager.hasDecisionPoint(firstBlock));

        decisionPointManager = new DefaultPlanManager(plan, new DefaultStrategyFactory());
        Assert.assertFalse(decisionPointManager.hasDecisionPoint(firstBlock));
    }

    private static void completePhase(Phase phase) {
        for (Block block : phase.getBlocks()) {
            ((TestBlock)block).setStatus(Status.COMPLETE);
        }
    }

    private static Plan getEmptyStage() {
        return DefaultPlan.fromArgs();
    }

    private static Plan getTestStage(Block phase0Block, Block phase1Block) {
        return DefaultPlan.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(phase0Block)),
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-1",
                        Arrays.asList(phase1Block)));
    }
}
