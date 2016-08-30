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
 * This class tests the {@link DefaultStageManager}.
 */
public class DefaultStageManagerTest {

    private TestBlock firstBlock, secondBlock;
    private Stage stage;
    private PhaseStrategyFactory stratFactory;
    private StageManager stageManager;

    @Mock
    Block mockBlock;

    @Mock
    Reconciler reconciler;

    @Before
    public void beforeEach() {
        firstBlock = new TestBlock();
        secondBlock = new TestBlock();
        stage = getTestStage(firstBlock, secondBlock);
        stratFactory = new DefaultStrategyFactory();
        stageManager = new DefaultStageManager(stage, stratFactory);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetStage() {
        Assert.assertEquals(2, stageManager.getStage().getPhases().size());
        stageManager.setStage(getEmptyStage());
        Assert.assertEquals(0, stageManager.getStage().getPhases().size());
        stageManager.setStage(getTestStage(firstBlock, secondBlock));
        Assert.assertEquals(2, stageManager.getStage().getPhases().size());
    }

    @Test
    public void testGetCurrentPhase() {
        Phase firstPhase = stage.getPhases().get(0);
        Phase secondPhase = stage.getPhases().get(1);
        Assert.assertEquals(firstPhase, stageManager.getCurrentPhase());

        completePhase(firstPhase);
        Assert.assertEquals(secondPhase, stageManager.getCurrentPhase());

        completePhase(secondPhase);
        Assert.assertNull(stageManager.getCurrentPhase());
    }

    @Test
    public void testGetPhaseStatus() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertEquals(Status.PENDING, stageManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, stageManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertEquals(Status.COMPLETE, stageManager.getPhaseStatus(firstPhase.getId()));
        // bad id:
        Assert.assertEquals(Status.ERROR, stageManager.getPhaseStatus(UUID.randomUUID()));
    }


    @Test
    public void testEmptyStageStatus() {
        StageManager emptyManager = new DefaultStageManager(getEmptyStage(), stratFactory);
        Assert.assertEquals(Status.COMPLETE, emptyManager.getStatus());
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.PENDING, stageManager.getStatus());
        firstBlock.setStatus(Status.ERROR);
        Assert.assertNull(stageManager.getStatus());
        firstBlock.setStatus(Status.WAITING);
        Assert.assertNull(stageManager.getStatus()); // error state: Blocks shouldn't be WAITING
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, stageManager.getStatus());
        completePhase(stage.getPhases().get(0));
        Assert.assertEquals(Status.IN_PROGRESS, stageManager.getStatus());
        completePhase(stage.getPhases().get(1));
        Assert.assertEquals(Status.COMPLETE, stageManager.getStatus());
    }

    @Test
    public void testInProgressStatus() {
        when(reconciler.isReconciled()).thenReturn(false);
        ReconciliationPhase reconciliationPhase = ReconciliationPhase.create(reconciler);
        Stage waitingStage = DefaultStage.fromArgs(
                reconciliationPhase,
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-1",
                        Arrays.asList(secondBlock)));

        Block reconciliationBlock = reconciliationPhase.getBlock(0);
        reconciliationBlock.start();
        Assert.assertTrue(reconciliationBlock.isInProgress());

        StageManager waitingManager = new DefaultStageManager(waitingStage, new StageStrategyFactory());
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getStatus());
    }

    @Test
    public void testGetCurrentBlock() {
        Assert.assertEquals(stage.getPhases().get(0).getBlock(0), stageManager.getCurrentBlock());
    }

    @Test
    public void testIsComplete() {
        Assert.assertFalse(stageManager.isComplete());
        completePhase(stage.getPhases().get(0));
        Assert.assertFalse(stageManager.isComplete());
        completePhase(stage.getPhases().get(1));
        Assert.assertTrue(stageManager.isComplete());
    }

    @Test
    public void testInterruptProceed() {
        Assert.assertFalse(stageManager.isInterrupted());
        stageManager.interrupt();
        Assert.assertTrue(stageManager.isInterrupted());
        stageManager.proceed();
        Assert.assertFalse(stageManager.isInterrupted());
    }

    @Test
    public void testRestart() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstBlock.isComplete());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testRestartBadIds() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstBlock.isComplete());
        stageManager.restart(firstPhase.getId(), UUID.randomUUID()); // bad block
        Assert.assertTrue(firstBlock.isComplete()); // no change
        stageManager.restart(UUID.randomUUID(), firstBlock.getId()); // bad phase
        Assert.assertTrue(firstBlock.isComplete()); // no change
        stageManager.restart(firstPhase.getId(), firstBlock.getId()); // correct
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testForceComplete() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testForceCompleteBadIds() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        stageManager.forceComplete(firstPhase.getId(), UUID.randomUUID()); // bad block
        Assert.assertTrue(firstBlock.isPending()); // no change
        stageManager.forceComplete(UUID.randomUUID(), firstBlock.getId()); // bad phase
        Assert.assertTrue(firstBlock.isPending()); // no change
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId()); // correct
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testUpdate() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Stage mockStage = DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        StageManager mockStageManager = new DefaultStageManager(mockStage, stratFactory);
        Protos.TaskStatus testStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.taskId)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();

        verify(mockBlock, times(0)).update(any());
        mockStageManager.update(testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testUpdateObserver() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Stage mockStage = DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        StageManager mockStageManager = new DefaultStageManager(mockStage, stratFactory);
        Protos.TaskStatus testStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.taskId)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();

        verify(mockBlock, times(0)).update(any());
        mockStageManager.update(null, testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testHasDecisionPoint() {
        Block firstBlock = stage.getPhases().get(0).getBlock(0);

        StageManager decisionPointManager = new DefaultStageManager(stage, new StageStrategyFactory());
        Assert.assertTrue(decisionPointManager.hasDecisionPoint(firstBlock));

        decisionPointManager = new DefaultStageManager(stage, new DefaultStrategyFactory());
        Assert.assertFalse(decisionPointManager.hasDecisionPoint(firstBlock));
    }

    private static void completePhase(Phase phase) {
        for (Block block : phase.getBlocks()) {
            ((TestBlock)block).setStatus(Status.COMPLETE);
        }
    }

    private static Stage getEmptyStage() {
        return DefaultStage.fromArgs();
    }

    private static Stage getTestStage(Block phase0Block, Block phase1Block) {
        return DefaultStage.fromArgs(
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
