package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.TaskStatusBuilder;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.UUID;

/**
 * This class tests the {@link DefaultStageManager}.
 */
public class DefaultStageManagerTest {

    private static String testTaskId = "test-task-id";
    private TestBlock firstBlock, secondBlock;
    private Stage stage;
    private PhaseStrategyFactory stratFactory;
    private StageManager stageManager;

    @Mock
    Block mockBlock;

    @Mock
    Reconciler reconciler;

    @Mock
    TaskStatusProvider taskProvider;

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
        Assert.assertEquals(Status.Pending, stageManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, stageManager.getPhaseStatus(firstPhase.getId()));
        firstBlock.setStatus(Status.Complete);
        Assert.assertEquals(Status.Complete, stageManager.getPhaseStatus(firstPhase.getId()));
    }


    @Test
    public void testEmptyStageStatus() {
        StageManager emptyManager = new DefaultStageManager(getEmptyStage(), stratFactory);
        Assert.assertEquals(Status.Complete, emptyManager.getStatus());
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.Pending, stageManager.getStatus());
        firstBlock.setStatus(Status.Error);
        Assert.assertNull(stageManager.getStatus());
        firstBlock.setStatus(Status.Waiting);
        Assert.assertNull(stageManager.getStatus()); // error state: Blocks shouldn't be Waiting
        firstBlock.setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, stageManager.getStatus());
        completePhase(stage.getPhases().get(0));
        Assert.assertEquals(Status.InProgress, stageManager.getStatus());
        completePhase(stage.getPhases().get(1));
        Assert.assertEquals(Status.Complete, stageManager.getStatus());
    }

    @Test
    public void testInProgressStatus() {
        when(reconciler.isReconciled()).thenReturn(false);
        ReconciliationPhase reconciliationPhase = ReconciliationPhase.create(reconciler, taskProvider);
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
        Assert.assertEquals(Status.InProgress, waitingManager.getStatus());
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
        firstBlock.setStatus(Status.Complete);
        Assert.assertTrue(firstBlock.isComplete());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.InProgress);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testForceComplete() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertTrue(firstBlock.isPending());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
        firstBlock.setStatus(Status.InProgress);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
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
        Protos.TaskStatus testStatus = TaskStatusBuilder.createTaskStatus(testTaskId, Protos.TaskState.TASK_RUNNING);

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
        Protos.TaskStatus testStatus = TaskStatusBuilder.createTaskStatus(testTaskId, Protos.TaskState.TASK_RUNNING);

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
            ((TestBlock)block).setStatus(Status.Complete);
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
